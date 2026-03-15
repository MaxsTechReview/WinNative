package com.winlator.cmod

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.app.ActivityOptionsCompat
import android.os.Bundle
import android.os.Process
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.winlator.cmod.widget.chasingBorder
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.steam.utils.getAvatarURL
import com.winlator.cmod.steam.data.SteamApp
import com.winlator.cmod.steam.data.DepotInfo
import com.winlator.cmod.steam.data.DownloadInfo
import com.winlator.cmod.steam.enums.DownloadPhase
import com.winlator.cmod.db.PluviaDatabase
import com.winlator.cmod.utils.StorageUtils
import com.winlator.cmod.utils.PeIconExtractor
import com.winlator.cmod.service.DownloadService
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.steam.events.EventDispatcher
import com.winlator.cmod.steam.events.AndroidEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import `in`.dragonbra.javasteam.enums.EPersonaState
import kotlin.math.abs
import com.winlator.cmod.epic.data.EpicGame
import com.winlator.cmod.steam.SteamLoginActivity
import com.winlator.cmod.epic.service.EpicAuthManager
import com.winlator.cmod.epic.service.EpicService
import com.winlator.cmod.epic.ui.auth.EpicOAuthActivity
import com.winlator.cmod.epic.service.EpicGameLauncher
import com.winlator.cmod.epic.service.EpicCloudSavesManager
import com.winlator.cmod.epic.service.EpicConstants
import com.winlator.cmod.epic.data.EpicCredentials
import com.winlator.cmod.epic.data.EpicGameToken
import com.winlator.cmod.epic.service.EpicDownloadManager
import com.winlator.cmod.epic.service.EpicManager
import com.winlator.cmod.gog.data.GOGGame
import com.winlator.cmod.gog.data.LibraryItem
import com.winlator.cmod.gog.service.GOGAuthManager
import com.winlator.cmod.gog.service.GOGConstants
import com.winlator.cmod.gog.service.GOGService
import com.winlator.cmod.gog.ui.auth.GOGOAuthActivity
import com.winlator.cmod.utils.ControllerHelper
import com.winlator.cmod.ui.FourByTwoGridView
import com.winlator.cmod.ui.JoystickGridScroll

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.WindowCompat
import kotlin.math.roundToInt

// Color palette
private val BgDark = Color(0xFF0F0F12)
private val SurfaceDark = Color(0xFF161B22)
private val CardDark = Color(0xFF14141E)
private val CardBorder = Color(0xFF21212E)
private val Accent = Color(0xFF1A9FFF)
private val AccentGlow = Color(0xFF58A6FF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)
private val DangerRed = Color(0xFFFF6B6B)
private val StatusOnline = Color(0xFF3FB950)
private val StatusAway = Color(0xFFF0C040)
private val StatusOffline = Color(0xFF6E7681)


private val LIBRARY_NAME_SANITIZE_REGEX = "[^A-Za-z0-9 _-]".toRegex()

enum class LibraryLayoutMode {
    GRID_4,
    CAROUSEL,
}

@AndroidEntryPoint
class UnifiedActivity : ComponentActivity() {
    @Inject lateinit var db: PluviaDatabase

    // Track the currently selected game in the carousel for Game Settings button
    private var selectedSteamAppId: Int = 0
    private var selectedSteamAppName: String = ""
    private var selectedLibrarySource: String = ""
    private var selectedGogGameId: String = ""
    
    // Trigger to refresh library when activity resumes from another container
    var libraryRefreshSignal by mutableIntStateOf(0)

    val rightStickScrollState = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val leftStickScrollState = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val keyEventFlow = kotlinx.coroutines.flow.MutableSharedFlow<android.view.KeyEvent>(extraBufferCapacity = 10)
    // Library grid focus: tracked index and item count, controlled by DPAD
    val libraryFocusIndex = kotlinx.coroutines.flow.MutableStateFlow(0)
    var libraryItemCount: Int = 0
    private var currentLibraryLayoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID_4
    private var lastLibraryMoveTime = 0L

    private fun moveLibraryFocus(left: Boolean, right: Boolean, up: Boolean, down: Boolean) {
        val idx = libraryFocusIndex.value
        val count = libraryItemCount
        if (count <= 0) return
        var newIdx = idx
        when (currentLibraryLayoutMode) {
            LibraryLayoutMode.GRID_4 -> {
                if (left) newIdx = (idx - 1).coerceAtLeast(0)
                if (right) newIdx = (idx + 1).coerceAtMost(count - 1)
                if (up) newIdx = (idx - 4).coerceAtLeast(0)
                if (down) newIdx = (idx + 4).coerceAtMost(count - 1)
            }
            LibraryLayoutMode.CAROUSEL -> {
                if (left) newIdx = (idx - 1).coerceAtLeast(0)
                if (right) newIdx = (idx + 1).coerceAtMost(count - 1)
            }
        }
        libraryFocusIndex.value = newIdx
    }

    private fun gogPseudoId(gameId: String): Int {
        val normalized = gameId.hashCode() and 0x1FFFFFFF
        return 1_500_000_000 + normalized
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        // Intercept keys we handle globally to prevent fall-through (e.g. Start button launching a game)
        val isHandledGlobally = when (keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_START,
            android.view.KeyEvent.KEYCODE_BUTTON_A,
            android.view.KeyEvent.KEYCODE_BUTTON_B,
            android.view.KeyEvent.KEYCODE_BUTTON_X,
            android.view.KeyEvent.KEYCODE_BUTTON_Y,
            android.view.KeyEvent.KEYCODE_BUTTON_L1,
            android.view.KeyEvent.KEYCODE_BUTTON_R1,
            android.view.KeyEvent.KEYCODE_BUTTON_L2,
            android.view.KeyEvent.KEYCODE_BUTTON_R2,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> true
            else -> false
        }

        // On library tab, intercept ALL DPAD events so focus can never leave the grid
        if (currentTabKey == "library") {
            val isDpad = keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
            if (isDpad) {
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    moveLibraryFocus(
                        left = keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        right = keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                        up = keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP,
                        down = keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    )
                }
                return true // consume both DOWN and UP
            }
        }

        if (action == android.view.KeyEvent.ACTION_DOWN) {
            if (isHandledGlobally) {
                keyEventFlow.tryEmit(event)
                return true
            }
        } else if (action == android.view.KeyEvent.ACTION_UP && isHandledGlobally) {
            // Consume ACTION_UP for handled keys to ensure balanced event stream for super
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        // Ensure all store services are running when returning from a game or other activity
        if (GOGService.hasStoredCredentials(this) && !GOGService.isRunning) {
            GOGService.start(this)
        }
        if (EpicService.hasStoredCredentials(this) && !EpicService.isRunning) {
            EpicService.start(this)
        }
        if (SteamService.isLoggedIn && SteamService.instance == null) {
            SteamService.start(this)
        }
        libraryRefreshSignal++
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if ((event.source and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK &&
            event.action == android.view.MotionEvent.ACTION_MOVE) {
            
            // Handle Right Joystick Y axis for scrolling in stores
            val rz = event.getAxisValue(android.view.MotionEvent.AXIS_RZ)
            rightStickScrollState.value = rz

            // Handle Left Joystick Y axis for scrolling in stores
            val leftY = event.getAxisValue(android.view.MotionEvent.AXIS_Y)
            leftStickScrollState.value = leftY

            // Handle Left Joystick/D-pad to emulate KeyEvents for Compose Focus
            val x = event.getAxisValue(android.view.MotionEvent.AXIS_X)
            val y = event.getAxisValue(android.view.MotionEvent.AXIS_Y)
            val hatX = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y)

            val isJoystickLeft = x < -0.5f
            val isJoystickRight = x > 0.5f
            val isJoystickUp = y < -0.5f
            val isJoystickDown = y > 0.5f

            val isHatLeft = hatX < -0.5f
            val isHatRight = hatX > 0.5f
            val isHatUp = hatY < -0.5f
            val isHatDown = hatY > 0.5f

            val now = event.eventTime
            
            // Check if we are on the library tab to restrict movement
            val isLibraryTab = currentTabKey == "library"
            val isStoreTab = currentTabKey == "store" || currentTabKey == "steam" || currentTabKey == "epic"

            // LIBRARY TAB: update grid focus index directly (no injectKeyEvent to avoid bypassing interception)
            if (isLibraryTab) {
                val count = libraryItemCount
                if (count > 0) {
                    if (isHatLeft || isHatRight || isHatUp || isHatDown) {
                        if (now - lastHatMoveTime > 150) {
                            moveLibraryFocus(isHatLeft, isHatRight, isHatUp, isHatDown)
                            lastHatMoveTime = now
                            return true
                        }
                    }
                    if (isJoystickLeft || isJoystickRight || isJoystickUp || isJoystickDown) {
                        if (now - lastJoystickMoveTime > 300) {
                            moveLibraryFocus(isJoystickLeft, isJoystickRight, isJoystickUp, isJoystickDown)
                            lastJoystickMoveTime = now
                            return true
                        }
                    }
                }
            } else {
                // NON-LIBRARY TABS: D-pad navigates focus
                if (isHatLeft || isHatRight || isHatUp || isHatDown) {
                    if (now - lastHatMoveTime > 150) {
                        if (isHatLeft) injectKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
                        if (isHatRight) injectKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                        if (isHatUp) injectKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_UP)
                        if (isHatDown) injectKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                        lastHatMoveTime = now
                        return true
                    }
                }

                // Left Joystick in Store tabs: 75% slower throttle (2400ms vs normal 600ms)
                // Non-store tabs: normal 600ms
                val joystickThrottle = if (isStoreTab) 2400L else 600L
                if (isJoystickLeft || isJoystickRight || isJoystickUp || isJoystickDown) {
                    if (now - lastJoystickMoveTime > joystickThrottle) {
                        if (isJoystickLeft) injectKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
                        if (isJoystickRight) injectKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                        if (isJoystickUp) injectKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_UP)
                        if (isJoystickDown) injectKeyEvent(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                        lastJoystickMoveTime = now
                        return true
                    }
                }
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private var currentTabKey: String = "library"

    private var lastHatMoveTime = 0L
    private var lastJoystickMoveTime = 0L

    private fun injectKeyEvent(keyCode: Int) {
        window.decorView.rootView.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        window.decorView.rootView.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = PluviaDatabase.getInstance(this)
        EpicAuthManager.updateLoginStatus(this)
        GOGAuthManager.updateLoginStatus(this)
        GOGConstants.init(this)
        
        // Start EpicService if user is logged in
        if (EpicService.hasStoredCredentials(this)) {
            EpicService.start(this)
        }

        // Start SteamService if user is logged in
        if (SteamService.isLoggedIn) {
            SteamService.start(this)
        }

        if (GOGAuthManager.isLoggedIn(this)) {
            GOGService.start(this)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = 0xFF0D1117.toInt()

        // Exclude left edge from system back gesture so the drawer can capture swipes
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.decorView.post {
                val leftEdgeWidth = (40 * resources.displayMetrics.density).toInt()
                val exclusionRect = android.graphics.Rect(0, 0, leftEdgeWidth, window.decorView.height)
                window.decorView.systemGestureExclusionRects = listOf(exclusionRect)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent,
                background = BgDark,
                surface = SurfaceDark,
                onSurface = TextPrimary
            )) {
                UnifiedHub()
            }
        }
    }

    // Tab definitions
    private data class TabDef(val label: String, val key: String)

    private fun buildTabs(aio: Boolean, storeVisible: Map<String, Boolean>): List<TabDef> {
        val base = mutableListOf(TabDef("Library", "library"), TabDef("Downloads", "downloads"))
        if (aio) {
            base.add(TabDef("Store", "store"))
        } else {
            if (storeVisible["steam"] != false) base.add(TabDef("Steam", "steam"))
            if (storeVisible["epic"] != false) base.add(TabDef("Epic", "epic"))
            if (storeVisible["gog"] != false) base.add(TabDef("GOG", "gog"))
            if (storeVisible["amazon"] != false) base.add(TabDef("Amazon", "amazon"))
        }
        return base
    }

    // Main scaffold
    @Composable
    fun UnifiedHub() {
        var aioMode by remember { mutableStateOf(PrefManager.aioStoreMode) }
        val storeVisible = remember { mutableStateMapOf("steam" to true, "epic" to true, "gog" to true, "amazon" to true) }
        var showAddCustomGame by remember { mutableStateOf(false) }
        var showExitDialog by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var libraryRefreshKey by remember { mutableIntStateOf(0) }
        var iconRefreshKey by remember { mutableIntStateOf(0) }
        
        val currentRefreshSignal = this@UnifiedActivity.libraryRefreshSignal
        LaunchedEffect(currentRefreshSignal) {
            libraryRefreshKey++
            iconRefreshKey++
        }
        
        val contentFilters = remember { mutableStateMapOf("games" to true, "dlc" to false, "applications" to false, "tools" to false) }
        var libraryLayoutMode by remember {
            mutableStateOf(
                runCatching { LibraryLayoutMode.valueOf(PrefManager.libraryLayoutMode) }
                    .getOrDefault(LibraryLayoutMode.GRID_4)
            )
        }
        val tabs = remember(aioMode, storeVisible.toMap()) { buildTabs(aioMode, storeVisible) }
        var selectedIdx by rememberSaveable { mutableIntStateOf(0) }
        var selectedDownloadId by remember { mutableStateOf<String?>(null) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val isLoggedIn by SteamService.isLoggedInFlow.collectAsState()
        val isEpicLoggedIn by EpicAuthManager.isLoggedInFlow.collectAsState()
        val isGogLoggedIn by GOGAuthManager.isLoggedInFlow.collectAsState()
        val steamApps by db.steamAppDao().getAllOwnedApps().collectAsState(initial = emptyList())
        val context = LocalContext.current
        val persona by SteamService.instance?.localPersona?.collectAsState()
            ?: remember { mutableStateOf(null) }
        val scope = rememberCoroutineScope()
        
        // Collect Epic/GOG apps from DB flows (Room flows auto-update on data changes)
        val epicApps by db.epicGameDao().getAll().collectAsState(initial = emptyList())
        val gogApps by db.gogGameDao().getAll().collectAsState(initial = emptyList())

        var isControllerConnected by remember { mutableStateOf(ControllerHelper.isControllerConnected()) }
        val isPS = remember(isControllerConnected) { ControllerHelper.isPlayStationController() }
        val isLibraryTab = tabs.getOrNull(selectedIdx)?.key == "library"

        // Refresh controller state periodically
        LaunchedEffect(Unit) {
            while(true) {
                isControllerConnected = ControllerHelper.isControllerConnected()
                kotlinx.coroutines.delay(2000)
            }
        }

        // Observe library install status changes to refresh UI
        LaunchedEffect(Unit) {
            val listener = object : EventDispatcher.JavaEventListener {
                override fun onEvent(event: Any) {
                    if (event is AndroidEvent.LibraryInstallStatusChanged) {
                        libraryRefreshKey++
                        iconRefreshKey++
                    }
                }
            }
            PluviaApp.events.onJava(AndroidEvent.LibraryInstallStatusChanged::class, listener)
        }

        LaunchedEffect(isEpicLoggedIn) {
            if (isEpicLoggedIn) {
                EpicService.start(context)
            }
        }

        LaunchedEffect(isGogLoggedIn) {
            if (isGogLoggedIn) {
                GOGService.start(context)
            }
        }

        val epicLoginLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val code = result.data?.getStringExtra(EpicOAuthActivity.EXTRA_AUTH_CODE)
                if (code != null) {
                    scope.launch {
                        val authResult = EpicAuthManager.authenticateWithCode(context, code)
                        if (authResult.isSuccess) {
                            android.widget.Toast.makeText(context, "Logged in to Epic Games!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Epic Login failed: ${authResult.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        val gogLoginLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val code = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_AUTH_CODE)
                if (!code.isNullOrBlank()) {
                    scope.launch {
                        val authResult = GOGAuthManager.authenticateWithCode(context, code)
                        if (authResult.isSuccess) {
                            GOGService.start(context)
                            android.widget.Toast.makeText(context, "Logged in to GOG!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "GOG Login failed: ${authResult.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        val filteredSteamApps = remember(steamApps, contentFilters.toMap()) {
            steamApps.filter { app ->
                when (app.type) {
                    com.winlator.cmod.steam.enums.AppType.game -> contentFilters["games"] == true
                    com.winlator.cmod.steam.enums.AppType.demo -> contentFilters["games"] == true
                    com.winlator.cmod.steam.enums.AppType.dlc -> contentFilters["dlc"] == true
                    com.winlator.cmod.steam.enums.AppType.application -> contentFilters["applications"] == true
                    com.winlator.cmod.steam.enums.AppType.tool -> contentFilters["tools"] == true
                    com.winlator.cmod.steam.enums.AppType.config -> contentFilters["tools"] == true
                    else -> contentFilters["games"] == true
                }
            }
        }

        // Sync Steam states periodically without forcing full library recomposition
        LaunchedEffect(Unit) {
            while(true) {
                kotlinx.coroutines.delay(10000)
                SteamService.syncStates()
            }
        }

        // Clamp selectedIdx if tabs shrink
        var globalSettingsApp by remember { mutableStateOf<SteamApp?>(null) }
        var globalSettingsGogGame by remember { mutableStateOf<GOGGame?>(null) }
        
        LaunchedEffect(tabs.size) { if (selectedIdx >= tabs.size) selectedIdx = 0 }
        LaunchedEffect(Unit) { SteamService.requestUserPersona() }

        val activity = LocalContext.current as? UnifiedActivity
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = 0)
        
        LaunchedEffect(tabs) {
            activity?.keyEventFlow?.collect { event ->
                val key = tabs.getOrNull(selectedIdx)?.key ?: "library"
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                        selectedIdx = if (selectedIdx > 0) selectedIdx - 1 else tabs.size - 1
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                        selectedIdx = (selectedIdx + 1) % tabs.size
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_START -> {
                        val intent = Intent(context, MainActivity::class.java)
                        intent.putExtra("selected_menu_item_id", R.id.main_menu_stores)
                        intent.putExtra("return_to_unified", true)
                        val opts = ActivityOptionsCompat.makeCustomAnimation(context, R.anim.settings_enter, R.anim.settings_exit)
                        context.startActivity(intent, opts.toBundle())
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_X -> {
                        if (key != "downloads") {
                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                        }
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_B -> {
                        // Close menus in order, or show exit confirmation if none are open
                        if (drawerState.isOpen) drawerState.close()
                        else if (globalSettingsApp != null) globalSettingsApp = null
                        else if (globalSettingsGogGame != null) globalSettingsGogGame = null
                        else if (showAddCustomGame) showAddCustomGame = false
                        else showExitDialog = true
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_Y -> {
                        if (key == "library" && (selectedSteamAppId != 0 || selectedGogGameId.isNotEmpty())) {
                            if (selectedLibrarySource == "GOG") {
                                globalSettingsGogGame = gogApps.find { it.id == selectedGogGameId }
                                return@collect
                            }
                            val isCustom = selectedSteamAppId < 0
                            val epicId = if (selectedSteamAppId >= 2000000000) selectedSteamAppId - 2000000000 else 0
                            
                            // Handle Steam, Custom, and Epic semi-unified logic for the settings dialog trigger
                            globalSettingsApp = (steamApps.find { it.id == selectedSteamAppId }
                                ?: if (isCustom) {
                                    SteamApp(id = selectedSteamAppId, name = selectedSteamAppName, developer = "Custom")
                                } else if (epicId > 0) {
                                    val epic = epicApps.find { it.id == epicId }
                                    SteamApp(
                                        id = selectedSteamAppId,
                                        name = selectedSteamAppName,
                                        developer = epic?.developer ?: "Epic Games",
                                        gameDir = epic?.installPath ?: ""
                                    )
                                } else null)
                        }
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                        if (key == "library" && (selectedSteamAppId != 0 || selectedGogGameId.isNotEmpty())) {
                            val isCustom = selectedSteamAppId < 0
                            val epicId = if (selectedSteamAppId >= 2000000000) selectedSteamAppId - 2000000000 else 0
                            val containerManager = ContainerManager(context)
                            if (isCustom) {
                                launchCustomGame(context, containerManager, selectedSteamAppName)
                            } else if (selectedLibrarySource == "GOG") {
                                gogApps.find { it.id == selectedGogGameId }?.let {
                                    launchGogGame(context, containerManager, it)
                                }
                            } else if (epicId > 0) {
                                val epic = epicApps.find { it.id == epicId }
                                if (epic != null && epic.isInstalled) {
                                    val dummyApp = SteamApp(id = selectedSteamAppId, name = selectedSteamAppName, gameDir = epic.installPath)
                                    launchSteamGame(context, containerManager, dummyApp)
                                }
                            } else {
                                val steam = steamApps.find { it.id == selectedSteamAppId }
                                if (steam != null && SteamService.isAppInstalled(steam.id)) {
                                    launchSteamGame(context, containerManager, steam)
                                }
                            }
                        }
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_L2 -> {
                        if (key == "downloads") {
                            val pausableDownloads = DownloadService.getAllDownloads().filter {
                                val status = it.second.getStatusFlow().value
                                status != DownloadPhase.COMPLETE && status != DownloadPhase.CANCELLED
                            }
                            if (pausableDownloads.isNotEmpty()) {
                                val allPausableDownloadsPaused = pausableDownloads.all {
                                    it.second.getStatusFlow().value == DownloadPhase.PAUSED
                                }
                                if (allPausableDownloadsPaused) {
                                    DownloadService.resumeAll()
                                } else {
                                    DownloadService.pauseAll()
                                }
                            }
                        }
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_R2 -> {
                        if (key == "downloads") {
                            DownloadService.cancelAll()
                        }
                    }
                }
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    persona = persona,
                    context = context,
                    scope = scope,
                    aioMode = aioMode,
                    onAioToggle = { aioMode = it; PrefManager.aioStoreMode = it },
                    storeVisible = storeVisible,
                    contentFilters = contentFilters,
                    libraryLayoutMode = libraryLayoutMode,
                    onLibraryLayoutSelected = {
                        libraryLayoutMode = it
                        PrefManager.libraryLayoutMode = it.name
                    },
                    onClose = { scope.launch { drawerState.close() } }
                )
            },
            scrimColor = Color.Black.copy(alpha = 0.5f),
            gesturesEnabled = true
        ) {
        Box(Modifier.fillMaxSize().background(BgDark)) {
            Scaffold(
                containerColor = BgDark,
                topBar = { TopBar(tabs, selectedIdx, { selectedIdx = it }, persona, context, scope, isControllerConnected, isPS, isLibraryTab, searchQuery, { searchQuery = it }, onFilterClicked = { scope.launch { drawerState.open() } }) {
                    if (selectedLibrarySource == "GOG") {
                        globalSettingsGogGame = gogApps.find { it.id == selectedGogGameId }
                    } else {
                        // Try Steam apps first, then fall back to custom or epic pseudo-apps
                        globalSettingsApp = (steamApps.find { it.id == selectedSteamAppId }
                        ?: if (selectedSteamAppId < 0) {
                            // Build a pseudo SteamApp for the custom game
                            SteamApp(
                                id = selectedSteamAppId,
                                name = selectedSteamAppName,
                                developer = "Custom"
                            )
                        } else if (selectedSteamAppId >= 2000000000) {
                            val epicId = selectedSteamAppId - 2000000000
                            val epic = epicApps.find { it.id == epicId }
                            SteamApp(
                                id = selectedSteamAppId,
                                name = selectedSteamAppName,
                                developer = epic?.developer ?: "Epic Games",
                                gameDir = epic?.installPath ?: ""
                            )
                        } else null)
                    }
                } }
            ) { padding ->
                LaunchedEffect(selectedIdx, tabs) {
                    currentTabKey = tabs.getOrNull(selectedIdx)?.key ?: "library"
                }

                Box(Modifier.padding(padding).fillMaxSize().background(BgDark)) {
                    val key = tabs.getOrNull(selectedIdx)?.key ?: "library"

                    AnimatedContent(
                        targetState = key,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        label = "tabContent"
                    ) { animatedKey ->
                        when (animatedKey) {
                        "library" -> LibraryCarousel(
                            isLoggedIn = isLoggedIn,
                            steamApps = filteredSteamApps,
                            epicApps = epicApps,
                            gogApps = gogApps,
                            layoutMode = libraryLayoutMode,
                            libraryRefreshKey = libraryRefreshKey,
                            iconRefreshKey = iconRefreshKey,
                            searchQuery = searchQuery
                        )
                            "downloads" -> DownloadsTab(selectedDownloadId, onSelectDownload = { selectedDownloadId = it })
                            "steam", "store" -> SteamStoreTab(isLoggedIn, filteredSteamApps, searchQuery)

                            "epic" -> EpicStoreTab(isEpicLoggedIn, searchQuery) {
                                epicLoginLauncher.launch(Intent(this@UnifiedActivity, EpicOAuthActivity::class.java))
                            }
                            "gog" -> GOGStoreTab(isGogLoggedIn, searchQuery) {
                                gogLoginLauncher.launch(Intent(this@UnifiedActivity, GOGOAuthActivity::class.java))
                            }
                            "amazon" -> StorePlaceholderTab("Amazon Games")
                        }
                    }

                    // Bottom-right Add Custom Game button
                    if (key == "library") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(52.dp)
                                .shadow(10.dp, CircleShape, spotColor = Accent.copy(alpha = 0.4f))
                                .clip(CircleShape)
                                .background(Accent)
                                .focusProperties { canFocus = false } // No specific button for this, handle via long press or touch
                                .clickable { showAddCustomGame = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Custom Game", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // Cloud Sync Dialog
                    val cloudSyncStatus by SteamService.cloudSyncStatus.collectAsState()
                    if (cloudSyncStatus != null) {
                        CloudSyncOverlay(cloudSyncStatus!!)
                    }
                }
            }

        }
        } // end ModalNavigationDrawer

        if (globalSettingsApp != null) {
            GameSettingsDialog(
                app = globalSettingsApp!!,
                onDismissRequest = { globalSettingsApp = null }
            )
        }
        if (globalSettingsGogGame != null) {
            GOGGameSettingsDialog(
                app = globalSettingsGogGame!!,
                onDismissRequest = { globalSettingsGogGame = null }
            )
        }

        if (showAddCustomGame) {
            AddCustomGameDialog(onDismiss = { showAddCustomGame = false; libraryRefreshKey++ })
        }

        // Back button exit confirmation
        BackHandler(enabled = true) {
            // Consistent behavior: close overlays first, then show exit confirmation
            if (drawerState.isOpen) {
                scope.launch { drawerState.close() }
            } else if (globalSettingsApp != null) {
                globalSettingsApp = null
            } else if (globalSettingsGogGame != null) {
                globalSettingsGogGame = null
            } else if (showAddCustomGame) {
                showAddCustomGame = false
            } else {
                showExitDialog = true
            }
        }

        if (showExitDialog) {
            Dialog(
                onDismissRequest = { showExitDialog = false },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Exit WinNative?",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Cancel button
                            OutlinedButton(
                                onClick = { showExitDialog = false },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Medium)
                            }
                            // Exit button
                            Button(
                                onClick = {
                                    // Kill all WinNative processes and close fully
                                    finishAffinity()
                                    Process.killProcess(Process.myPid())
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Exit", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Top bar
    @Composable
    private fun TopBar(
        tabs: List<TabDef>,
        selectedIdx: Int,
        onSelect: (Int) -> Unit,
        persona: com.winlator.cmod.steam.data.SteamFriend?,
        context: android.content.Context,
        scope: kotlinx.coroutines.CoroutineScope,
        isControllerConnected: Boolean,
        isPS: Boolean,
        isLibraryTab: Boolean,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        onFilterClicked: () -> Unit,
        onGameSettingsClicked: () -> Unit
    ) {
        var isSearchExpanded by remember { mutableStateOf(false) }
        val searchFocusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val isDownloadsTab = tabs.getOrNull(selectedIdx)?.key == "downloads"

        // Auto-collapse search when switching tabs
        LaunchedEffect(selectedIdx) {
            if (isSearchExpanded) {
                onSearchQueryChange("")
                isSearchExpanded = false
            }
        }

        // Auto-focus the search field when expanded
        LaunchedEffect(isSearchExpanded) {
            if (isSearchExpanded) {
                kotlinx.coroutines.delay(150)
                searchFocusRequester.requestFocus()
            } else if (searchQuery.isNotEmpty()) {
                onSearchQueryChange("")
            }
        }


        Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .height(64.dp)
                .padding(horizontal = 8.dp)
        ) {
            // Center Block: Tabs (absolutely centered, unaffected by left/right content)
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isControllerConnected) {
                    ControllerBadge("L1")
                    Spacer(Modifier.width(8.dp))
                }
                @Suppress("DEPRECATION")
                CompositionLocalProvider(
                    androidx.compose.material3.LocalRippleConfiguration provides null
                ) {
                    val tabWidth = 100.dp
                    val visibleCount = minOf(3, tabs.size)
                    val tabListState = rememberLazyListState()
                    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = tabListState)

                    LaunchedEffect(selectedIdx) {
                        val scrollTo = maxOf(0, selectedIdx - 1)
                        tabListState.animateScrollToItem(scrollTo)
                    }

                    Box(
                        modifier = Modifier
                            .width(tabWidth * visibleCount)
                            .height(44.dp)
                            .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.5f))
                            .clip(RoundedCornerShape(24.dp))
                            .background(CardDark)
                            .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    ) {
                        LazyRow(
                            state = tabListState,
                            flingBehavior = snapFlingBehavior,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusProperties { canFocus = !isLibraryTab },
                            userScrollEnabled = tabs.size > visibleCount
                        ) {
                            itemsIndexed(tabs) { index, tab ->
                                val selected = selectedIdx == index
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val tabScale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.92f else 1f,
                                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                                    label = "tabScale"
                                )
                                val textColor by animateColorAsState(
                                    targetValue = if (selected) Accent else TextSecondary,
                                    animationSpec = tween(280),
                                    label = "tabTextColor"
                                )

                                Box(
                                    modifier = Modifier
                                        .width(tabWidth)
                                        .fillMaxHeight()
                                        .focusProperties { canFocus = false }
                                        .graphicsLayer {
                                            scaleX = tabScale
                                            scaleY = tabScale
                                        }
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) { onSelect(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.label.uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
                if (isControllerConnected) {
                    Spacer(Modifier.width(8.dp))
                    ControllerBadge("R1")
                }
            }

            // Left Block: Settings & Search
            Row(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settings Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .focusProperties { canFocus = !isLibraryTab },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = {
                        val intent = Intent(context, MainActivity::class.java)
                        intent.putExtra("selected_menu_item_id", R.id.main_menu_stores)
                        intent.putExtra("return_to_unified", true)
                        val opts = ActivityOptionsCompat.makeCustomAnimation(context, R.anim.settings_enter, R.anim.settings_exit)
                        context.startActivity(intent, opts.toBundle())
                    }, modifier = Modifier.size(44.dp), enabled = true) {
                        Icon(Icons.Default.Settings, contentDescription = "Menu", tint = TextPrimary, modifier = Modifier.size(24.dp))
                    }
                }
                if (isControllerConnected) {
                    Spacer(Modifier.width(8.dp))
                    ControllerBadge(if (isPS) "Options" else "Start")
                }

                // Search Button (disabled on downloads tab)
                Spacer(Modifier.width(12.dp))

                val searchIconRotation by animateFloatAsState(
                    targetValue = if (isSearchExpanded) 90f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "searchIconRotation"
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(
                            if (isDownloadsTab) SurfaceDark.copy(alpha = 0.4f)
                            else if (isSearchExpanded) Accent.copy(alpha = 0.15f)
                            else SurfaceDark
                        )
                        .focusProperties { canFocus = !isLibraryTab },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (!isDownloadsTab) {
                                if (isSearchExpanded) {
                                    onSearchQueryChange("")
                                    isSearchExpanded = false
                                } else {
                                    isSearchExpanded = true
                                }
                            }
                        },
                        modifier = Modifier.size(44.dp),
                        enabled = !isDownloadsTab
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isDownloadsTab) TextSecondary.copy(alpha = 0.4f)
                                   else if (isSearchExpanded) Accent
                                   else TextPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = searchIconRotation }
                        )
                    }
                }
            }

            // Right Block: Status & Actions
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isStore = tabs.getOrNull(selectedIdx)?.label?.contains("Store", ignoreCase = true) == true
                if (isControllerConnected && !isStore) {
                    ControllerBadge(if (isPS) "Triangle" else "Y")
                    Spacer(Modifier.width(8.dp))
                }
                
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .focusProperties { canFocus = !isLibraryTab },
                    contentAlignment = Alignment.Center
                ) {
                    if (isStore) {
                        IconButton(onClick = { SteamService.requestSync() }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Store", tint = TextPrimary, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        IconButton(onClick = {
                            if (selectedSteamAppId != 0 || selectedGogGameId.isNotEmpty()) {
                                onGameSettingsClicked()
                            } else {
                                android.widget.Toast.makeText(context, "Select a game from your library first", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.Tune, contentDescription = "Game Settings", tint = TextPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Filter button (opens drawer)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .focusProperties { canFocus = !isLibraryTab }
                        .clickable { onFilterClicked() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = TextPrimary, modifier = Modifier.size(24.dp))
                }
                if (isControllerConnected) {
                    Spacer(Modifier.width(8.dp))
                    ControllerBadge(if (isPS) "Square" else "X")
                }
            }
        }

        // Dropdown Search Bar
        AnimatedVisibility(
            visible = isSearchExpanded && !isDownloadsTab,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(80)),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(60))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(0.7f)
                        .height(44.dp)
                        .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.4f))
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceDark),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 15.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                            cursorBrush = Brush.verticalGradient(listOf(Accent, AccentGlow)),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search games...",
                                            style = TextStyle(
                                                color = TextSecondary,
                                                fontSize = 15.sp
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        } // end Column
    }

    // PS5-style Library Carousel
    @Composable
    fun LibraryCarousel(
        isLoggedIn: Boolean,
        steamApps: List<SteamApp>,
        epicApps: List<EpicGame>,
        gogApps: List<GOGGame>,
        layoutMode: LibraryLayoutMode,
        libraryRefreshKey: Int = 0,
        iconRefreshKey: Int = 0,
        searchQuery: String = "",
    ) {
        val context = LocalContext.current

        // Load all shortcuts once and cache for both custom app discovery and GameCapsule icon lookup
        var cachedShortcuts by remember { mutableStateOf<List<Shortcut>>(emptyList()) }
        var customApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        LaunchedEffect(libraryRefreshKey) {
            withContext(Dispatchers.IO) {
                try {
                    val cm = ContainerManager(context)
                    val allShortcuts = cm.loadShortcuts()
                    val apps = allShortcuts
                        .filter { it.getExtra("game_source") == "CUSTOM" }
                        .map { shortcut ->
                            val displayName = shortcut.getExtra("custom_name", shortcut.name)
                            val customId = -(displayName.hashCode().and(0x7FFFFFFF) + 1)
                            SteamApp(
                                id = customId,
                                name = displayName,
                                developer = "Custom",
                                gameDir = shortcut.getExtra("custom_game_folder", "")
                            )
                        }
                    withContext(Dispatchers.Main) {
                        cachedShortcuts = allShortcuts
                        customApps = apps
                    }
                } catch (_: Exception) {}
            }
        }

        // Move expensive filtering (runBlocking DB queries, file I/O) off the main thread
        var installedApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        var gogByPseudoId by remember { mutableStateOf<Map<Int, GOGGame>>(emptyMap()) }

        LaunchedEffect(steamApps, epicApps, gogApps, customApps, libraryRefreshKey) {
            withContext(Dispatchers.IO) {
                val steamInstalled = steamApps.filter { SteamService.isAppInstalled(it.id) }

                val epicInstalled = epicApps.filter { it.isInstalled }

                val gogInstalled = gogApps.filter { it.isInstalled && java.io.File(it.installPath).exists() }

                val gogMap = gogInstalled.associateBy { gogPseudoId(it.id) }

                val playtimePrefs = context.getSharedPreferences("playtime_stats", android.content.Context.MODE_PRIVATE)
                val allPlaytime = playtimePrefs.all
                val mappedEpic = epicInstalled.map { epic ->
                    SteamApp(
                        id = 2000000000 + epic.id,
                        name = epic.title,
                        developer = epic.developer,
                        gameDir = epic.installPath
                    )
                }
                val mappedGog = gogInstalled.map { gog ->
                    SteamApp(
                        id = gogPseudoId(gog.id),
                        name = gog.title,
                        developer = gog.developer,
                        gameDir = gog.installPath,
                    )
                }
                val merged = steamInstalled + customApps + mappedEpic + mappedGog
                val sorted = merged.sortedByDescending { app ->
                    val searchKey = if (app.id >= 2000000000 || app.id < 0) {
                        app.name
                    } else {
                        app.name.replace(LIBRARY_NAME_SANITIZE_REGEX, "")
                    }
                    (allPlaytime["${searchKey}_last_played"] as? Long) ?: 0L
                }

                withContext(Dispatchers.Main) {
                    gogByPseudoId = gogMap
                    installedApps = sorted
                }
            }
        }

        val displayedApps = remember(installedApps, searchQuery) {
            if (searchQuery.isBlank()) installedApps
            else installedApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        if (installedApps.isEmpty()) {
            if (!isLoggedIn) {
                LoginRequiredScreen("Library") {
                    // Redirect to the Stores section in settings
                    val intent = Intent(this@UnifiedActivity, MainActivity::class.java)
                    intent.putExtra("selected_menu_item_id", R.id.main_menu_stores)
                    intent.putExtra("return_to_unified", true)
                    val opts = ActivityOptionsCompat.makeCustomAnimation(this@UnifiedActivity, R.anim.settings_enter, R.anim.settings_exit)
                    startActivity(intent, opts.toBundle())
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateMessage("No games installed. Use a Store tab or the + button to add games.")
                }
            }
            return
        }

        var selectedAppForSettings by remember { mutableStateOf<SteamApp?>(null) }
        var selectedGogGameForSettings by remember { mutableStateOf<GOGGame?>(null) }
        val gridState = rememberLazyGridState()
        val carouselState = rememberLazyListState()
        val activity = LocalContext.current as? UnifiedActivity

        LaunchedEffect(layoutMode) {
            currentLibraryLayoutMode = layoutMode
        }

        // Keep activity's item count in sync
        LaunchedEffect(displayedApps.size) {
            activity?.libraryItemCount = displayedApps.size
            val lastIndex = (displayedApps.size - 1).coerceAtLeast(0)
            if (activity != null && displayedApps.isNotEmpty() && activity.libraryFocusIndex.value > lastIndex) {
                activity.libraryFocusIndex.value = lastIndex
            }
        }

        // FocusRequesters for each grid item
        val focusRequesters = remember(displayedApps.size) {
            List(displayedApps.size) { FocusRequester() }
        }

        // Observe focus index changes from the activity and request focus on the target item
        val focusIndex by (activity?.libraryFocusIndex ?: kotlinx.coroutines.flow.MutableStateFlow(0)).collectAsState()
        LaunchedEffect(focusIndex, focusRequesters.size, layoutMode) {
            if (layoutMode == LibraryLayoutMode.GRID_4 &&
                focusRequesters.isNotEmpty() &&
                focusIndex in focusRequesters.indices
            ) {
                gridState.animateScrollToItem(focusIndex)
                try { focusRequesters[focusIndex].requestFocus() } catch (_: Exception) {}
            }
        }

        // Track selected app for the top-right Game Settings button
        LaunchedEffect(focusIndex, displayedApps) {
            val app = displayedApps.getOrNull(focusIndex) ?: displayedApps.firstOrNull()
            selectedSteamAppId = app?.id ?: 0
            selectedSteamAppName = app?.name ?: ""
            val gogGame = app?.let { gogByPseudoId[it.id] }
            selectedLibrarySource = when {
                gogGame != null -> "GOG"
                app == null -> ""
                app.id >= 2000000000 -> "EPIC"
                app.id < 0 -> "CUSTOM"
                else -> "STEAM"
            }
            selectedGogGameId = gogGame?.id.orEmpty()
        }

        val openSettingsForApp: (Int, SteamApp) -> Unit = { index, app ->
            activity?.libraryFocusIndex?.value = index
            selectedSteamAppId = app.id
            selectedSteamAppName = app.name
            val gogGame = gogByPseudoId[app.id]
            selectedLibrarySource = when {
                gogGame != null -> "GOG"
                app.id >= 2000000000 -> "EPIC"
                app.id < 0 -> "CUSTOM"
                else -> "STEAM"
            }
            selectedGogGameId = gogGame?.id.orEmpty()

            if (gogGame != null) {
                selectedGogGameForSettings = gogGame
            } else {
                selectedAppForSettings = app
            }
        }

        when (layoutMode) {
            LibraryLayoutMode.GRID_4 -> {
                FourByTwoGridView(
                    items = displayedApps,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    gridState = gridState,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    clipContent = false,
                ) { app, index, rowHeight ->
                    GameCapsule(
                        app = app,
                        gogGame = gogByPseudoId[app.id],
                        iconRefreshKey = iconRefreshKey,
                        isFocusedOverride = index == focusIndex,
                        shortcuts = cachedShortcuts,
                        onLongClick = {
                            openSettingsForApp(index, app)
                        },
                        modifier = Modifier
                            .height(rowHeight)
                            .then(
                                if (index in focusRequesters.indices)
                                    Modifier.focusRequester(focusRequesters[index])
                                else Modifier
                            )
                    )
                }
            }
            LibraryLayoutMode.CAROUSEL -> {
                LibraryCoverCarousel(
                    apps = displayedApps,
                    gogByPseudoId = gogByPseudoId,
                    iconRefreshKey = iconRefreshKey,
                    shortcuts = cachedShortcuts,
                    listState = carouselState,
                    selectedIndex = focusIndex,
                    focusRequesters = focusRequesters,
                    onLongClick = openSettingsForApp,
                    onCenteredIndexChanged = { centeredIndex ->
                        if (activity != null && activity.libraryFocusIndex.value != centeredIndex) {
                            activity.libraryFocusIndex.value = centeredIndex
                        }
                    },
                )
            }
        }

        if (selectedAppForSettings != null) {
            GameSettingsDialog(
                app = selectedAppForSettings!!,
                onDismissRequest = { selectedAppForSettings = null }
            )
        }
        if (selectedGogGameForSettings != null) {
            GOGGameSettingsDialog(
                app = selectedGogGameForSettings!!,
                onDismissRequest = { selectedGogGameForSettings = null }
            )
        }
    }

    @Composable
    private fun LibraryCoverCarousel(
        apps: List<SteamApp>,
        gogByPseudoId: Map<Int, GOGGame>,
        iconRefreshKey: Int,
        shortcuts: List<Shortcut>,
        listState: androidx.compose.foundation.lazy.LazyListState,
        selectedIndex: Int,
        focusRequesters: List<FocusRequester>,
        onLongClick: (Int, SteamApp) -> Unit,
        onCenteredIndexChanged: (Int) -> Unit,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 20.dp)
        ) {
            val spacing = 14.dp
            val baseCardWidth = maxWidth * 0.22f
            val baseCardHeight = baseCardWidth * 1.2f
            val sidePadding = ((maxWidth - baseCardWidth) / 2).coerceAtLeast(0.dp)
            val flingBehavior = rememberSnapFlingBehavior(listState)

            LaunchedEffect(selectedIndex, apps.size) {
                if (selectedIndex in apps.indices && !listState.isScrollInProgress) {
                    listState.animateScrollToItem(selectedIndex)
                }
            }

            LaunchedEffect(listState, apps.size) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .collect { visibleItems ->
                        if (visibleItems.isEmpty()) return@collect
                        val layoutInfo = listState.layoutInfo
                        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                        val centeredItem = visibleItems.minByOrNull { item ->
                            kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                        } ?: return@collect
                        onCenteredIndexChanged(centeredItem.index)
                    }
            }

            LaunchedEffect(listState, apps.size) {
                snapshotFlow { listState.isScrollInProgress }
                    .collect { isScrolling ->
                        if (!isScrolling) {
                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            if (visibleItems.isEmpty()) return@collect
                            val viewportCenter =
                                (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                            val centeredItem = visibleItems.minByOrNull { item ->
                                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                            } ?: return@collect
                            listState.animateScrollToItem(centeredItem.index)
                        }
                    }
            }

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 18.dp, bottom = 18.dp),
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(apps) { index, app ->
                    val isSelected = index == selectedIndex
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.1f else 0.9f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "carouselScale"
                    )
                    val rise by animateDpAsState(
                        targetValue = if (isSelected) 14.dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "carouselRise"
                    )

                    Box(
                        modifier = Modifier
                            .width(baseCardWidth)
                            .height(baseCardHeight + 28.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationY = -rise.toPx()
                            }
                    ) {
                        GameCapsule(
                            app = app,
                            gogGame = gogByPseudoId[app.id],
                            iconRefreshKey = iconRefreshKey,
                            isFocusedOverride = isSelected,
                            shortcuts = shortcuts,
                            onLongClick = {
                                onLongClick(index, app)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (index in focusRequesters.indices)
                                        Modifier.focusRequester(focusRequesters[index])
                                    else Modifier
                                )
                        )
                    }
                }
            }
        }
    }

    private enum class GameSettingsScreen {
        Menu,
        Saves,
        Uninstall,
    }

    private data class GameSettingsActionItem(
        val title: String,
        val icon: ImageVector,
        val accentColor: Color = Accent,
        val onClick: () -> Unit,
    )

    @Composable
    private fun GameSettingsDialogFrame(
        title: String,
        subtitle: String,
        sectionTitle: String,
        onDismissRequest: () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 760.dp),
                shape = RoundedCornerShape(20.dp),
                color = CardDark,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceDark),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextPrimary,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp),
                        color = SurfaceDark,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp)
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = sectionTitle.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                            )
                            content()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GameSettingsActionGrid(
        actions: List<GameSettingsActionItem>,
        modifier: Modifier = Modifier,
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            actions.chunked(3).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(3) { index ->
                        val action = rowActions.getOrNull(index)
                        if (action != null) {
                            GameSettingsActionCard(
                                action = action,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GameSettingsActionCard(
        action: GameSettingsActionItem,
        modifier: Modifier = Modifier,
    ) {
        val isDanger = action.accentColor == DangerRed
        val borderColor = if (isDanger) DangerRed.copy(alpha = 0.4f) else CardBorder
        val iconBg = if (isDanger) DangerRed.copy(alpha = 0.16f) else action.accentColor.copy(alpha = 0.14f)

        Surface(
            modifier = modifier
                .height(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = action.onClick),
            color = CardDark,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, borderColor),
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = action.accentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Text(
                    text = action.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    private fun GameSettingsInfoCard(
        message: String,
        accentColor: Color = Accent,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardDark,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
    }

    // Game Settings Dialog
    @Composable
    private fun GameSettingsDialog(app: SteamApp, onDismissRequest: () -> Unit) {
        val context = LocalContext.current
        var currentTab by remember { mutableStateOf(GameSettingsScreen.Menu) }
        val scope = rememberCoroutineScope()
        val isCustom = app.id < 0
        val isEpic = app.id >= 2000000000
        val epicId = if (isEpic) app.id - 2000000000 else 0
        val epicArtworkUrl by produceState<String?>(initialValue = null, key1 = isEpic, key2 = epicId) {
            value = if (isEpic) {
                val epicGame = db.epicGameDao().getById(epicId)
                epicGame?.primaryImageUrl ?: epicGame?.iconUrl
            } else {
                null
            }
        }
        
        // Export logic
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val os = context.contentResolver.openOutputStream(uri) ?: return@launch
                        val zos = java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(os))

                        val containerManager = com.winlator.cmod.container.ContainerManager(context)
                        val shortcut = when {
                            isCustom -> containerManager.loadShortcuts().find {
                                it.getExtra("game_source") == "CUSTOM" && (it.getExtra("custom_name") == app.name || it.name == app.name)
                            }
                            isEpic -> containerManager.loadShortcuts().find {
                                it.getExtra("game_source") == "EPIC" && it.getExtra("app_id") == epicId.toString()
                            }
                            else -> containerManager.loadShortcuts().find {
                                it.getExtra("app_id") == app.id.toString()
                            }
                        }
                        
                        val dirsToZip = mutableListOf<java.io.File>()
                        
                        // Goldberg saves: SteamService.getAppDirPath(app.id)/steam_settings/saves
                        val goldbergSaves = java.io.File(SteamService.getAppDirPath(app.id), "steam_settings/saves")
                        if (goldbergSaves.exists() && goldbergSaves.isDirectory) {
                            dirsToZip.add(goldbergSaves)
                        }

                        // Also prefix documents/saved games/appdata if shortcut exists
                        if (shortcut != null) {
                            val prefixDir = java.io.File(shortcut.container.getRootDir(), ".wine/drive_c/users/xuser")
                            val docs = java.io.File(prefixDir, "Documents")
                            val savedGames = java.io.File(prefixDir, "Saved Games")
                            val appData = java.io.File(prefixDir, "AppData")
                            if (docs.exists()) dirsToZip.add(docs)
                            if (savedGames.exists()) dirsToZip.add(savedGames)
                            if (appData.exists()) dirsToZip.add(appData)
                        }

                        // recursive zip function
                        fun zipDir(dir: java.io.File, baseName: String) {
                            val children = dir.listFiles() ?: return
                            for (child in children) {
                                val name = if (baseName.isEmpty()) child.name else "$baseName/${child.name}"
                                if (child.isDirectory) {
                                    zos.putNextEntry(java.util.zip.ZipEntry("$name/"))
                                    zos.closeEntry()
                                    zipDir(child, name)
                                } else {
                                    zos.putNextEntry(java.util.zip.ZipEntry(name))
                                    val fis = java.io.FileInputStream(child)
                                    val buf = ByteArray(1024 * 8)
                                    var len: Int
                                    while (fis.read(buf).also { len = it } > 0) {
                                        zos.write(buf, 0, len)
                                    }
                                    fis.close()
                                    zos.closeEntry()
                                }
                            }
                        }

                        for (dir in dirsToZip) {
                            // We put them in a folder under the zip by their semantic name
                            val baseName = dir.name // e.g. "saves", "Documents"
                            zos.putNextEntry(java.util.zip.ZipEntry("$baseName/"))
                            zos.closeEntry()
                            zipDir(dir, baseName)
                        }
                        
                        zos.close()
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Saves exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Failed to export saves: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        // Import logic
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val `is` = context.contentResolver.openInputStream(uri) ?: return@launch
                        val zis = java.util.zip.ZipInputStream(java.io.BufferedInputStream(`is`))
                        
                        val containerManager = com.winlator.cmod.container.ContainerManager(context)
                        val shortcut = when {
                            isCustom -> containerManager.loadShortcuts().find {
                                it.getExtra("game_source") == "CUSTOM" && (it.getExtra("custom_name") == app.name || it.name == app.name)
                            }
                            isEpic -> containerManager.loadShortcuts().find {
                                it.getExtra("game_source") == "EPIC" && it.getExtra("app_id") == epicId.toString()
                            }
                            else -> containerManager.loadShortcuts().find {
                                it.getExtra("app_id") == app.id.toString()
                            }
                        }
                        
                        val goldbergSavesParent = java.io.File(if (isEpic) app.gameDir else SteamService.getAppDirPath(app.id), if (isEpic) "" else "steam_settings")
                        val prefixDir = shortcut?.let { java.io.File(it.container.getRootDir(), ".wine/drive_c/users/xuser") }

                        var ze: java.util.zip.ZipEntry?
                        while (zis.nextEntry.also { ze = it } != null) {
                            val entry = ze!!
                            val name = entry.name
                            // Determine destination
                            var destFile: java.io.File? = null
                            if (name.startsWith("saves/")) {
                                destFile = java.io.File(goldbergSavesParent, name)
                            } else if (prefixDir != null) {
                                if (name.startsWith("Documents/") || name.startsWith("Saved Games/") || name.startsWith("AppData/")) {
                                    destFile = java.io.File(prefixDir, name)
                                }
                            }
                            
                            if (destFile != null) {
                                if (entry.isDirectory) {
                                    destFile.mkdirs()
                                } else {
                                    destFile.parentFile?.mkdirs()
                                    val fos = java.io.FileOutputStream(destFile)
                                    val buf = ByteArray(1024 * 8)
                                    var len: Int
                                    while (zis.read(buf).also { len = it } > 0) {
                                        fos.write(buf, 0, len)
                                    }
                                    fos.close()
                                }
                            }
                            zis.closeEntry()
                        }
                        zis.close()
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Saves imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Failed to import saves: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        val dialogSubtitle = when {
            isCustom -> "Custom game profile and local save management."
            isEpic -> "Epic Games profile, shortcut tools, and local saves."
            else -> "Steam profile, shortcut tools, and local saves."
        }

        GameSettingsDialogFrame(
            title = app.name,
            subtitle = dialogSubtitle,
            sectionTitle = when (currentTab) {
                GameSettingsScreen.Menu -> "Actions"
                GameSettingsScreen.Saves -> "Save Management"
                GameSettingsScreen.Uninstall -> if (isCustom) "Remove Game" else "Uninstall Game"
            },
            onDismissRequest = onDismissRequest,
        ) {
            when (currentTab) {
                GameSettingsScreen.Menu -> {
                    val actions = listOf(
                        GameSettingsActionItem(
                            title = "Settings",
                            icon = Icons.Default.Settings,
                            onClick = {
                                if (isCustom || isEpic) {
                                    val cm = ContainerManager(context)
                                    val sc = cm.loadShortcuts().find {
                                        if (isCustom) {
                                            it.getExtra("game_source") == "CUSTOM" &&
                                                (it.getExtra("custom_name") == app.name || it.name == app.name)
                                        } else {
                                            it.getExtra("game_source") == "EPIC" &&
                                                it.getExtra("app_id") == epicId.toString()
                                        }
                                    }
                                    if (sc != null) {
                                        val intent = Intent(context, MainActivity::class.java)
                                        intent.putExtra("edit_shortcut_path", sc.file.absolutePath)
                                        intent.putExtra("return_to_unified", true)
                                        val opts = ActivityOptionsCompat.makeCustomAnimation(
                                            context,
                                            R.anim.settings_enter,
                                            R.anim.settings_exit,
                                        )
                                        context.startActivity(intent, opts.toBundle())
                                    } else if (isEpic) {
                                        val intent = Intent(context, MainActivity::class.java)
                                        intent.putExtra("create_shortcut_for_epic_id", epicId)
                                        intent.putExtra("create_shortcut_for_app_name", app.name)
                                        intent.putExtra("return_to_unified", true)
                                        val opts = ActivityOptionsCompat.makeCustomAnimation(
                                            context,
                                            R.anim.settings_enter,
                                            R.anim.settings_exit,
                                        )
                                        context.startActivity(intent, opts.toBundle())
                                    }
                                } else {
                                    val intent = Intent(context, MainActivity::class.java)
                                    intent.putExtra("create_shortcut_for_app_id", app.id)
                                    intent.putExtra("create_shortcut_for_app_name", app.name)
                                    intent.putExtra("return_to_unified", true)
                                    val opts = ActivityOptionsCompat.makeCustomAnimation(
                                        context,
                                        R.anim.settings_enter,
                                        R.anim.settings_exit,
                                    )
                                    context.startActivity(intent, opts.toBundle())
                                }
                                onDismissRequest()
                            },
                        ),
                        GameSettingsActionItem(
                            title = "Shortcut",
                            icon = Icons.Default.Home,
                            onClick = {
                                scope.launch {
                                    val created = withContext(Dispatchers.IO) {
                                        addLibraryShortcutToHomeScreen(
                                            context,
                                            app,
                                            isCustom,
                                            isEpic,
                                            epicId,
                                            epicArtworkUrl,
                                        )
                                    }
                                    val message = if (created) {
                                        context.getString(R.string.library_shortcut_created)
                                    } else {
                                        context.getString(
                                            R.string.library_failed_to_create_shortcut,
                                            app.name,
                                        )
                                    }
                                    android.widget.Toast.makeText(
                                        context,
                                        message,
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        ),
                        GameSettingsActionItem(
                            title = "Saves",
                            icon = Icons.Default.Save,
                            onClick = { currentTab = GameSettingsScreen.Saves },
                        ),
                        GameSettingsActionItem(
                            title = if (isCustom) "Remove" else "Uninstall",
                            icon = Icons.Default.Delete,
                            accentColor = DangerRed,
                            onClick = { currentTab = GameSettingsScreen.Uninstall },
                        ),
                    )

                    GameSettingsActionGrid(actions = actions)
                }

                GameSettingsScreen.Saves -> {
                    GameSettingsActionGrid(
                        actions = listOf(
                            GameSettingsActionItem(
                                title = "Export",
                                icon = Icons.Default.Upload,
                                onClick = {
                                    exportLauncher.launch(
                                        "${app.name.replace(" ", "_").replace(":", "")}_Saves.zip",
                                    )
                                },
                            ),
                            GameSettingsActionItem(
                                title = "Import",
                                icon = Icons.Default.Download,
                                onClick = { importLauncher.launch(arrayOf("application/zip")) },
                            ),
                            GameSettingsActionItem(
                                title = "Back",
                                icon = Icons.Default.ArrowBack,
                                onClick = { currentTab = GameSettingsScreen.Menu },
                            ),
                        ),
                    )
                }

                GameSettingsScreen.Uninstall -> {
                    var isUninstalling by remember { mutableStateOf(false) }

                    GameSettingsInfoCard(
                        message = if (isCustom) {
                            "Remove ${app.name} from your library. Game files on disk will stay untouched."
                        } else {
                            "Uninstall ${app.name} and permanently delete its installed game folder."
                        },
                        accentColor = DangerRed,
                    )

                    if (isUninstalling) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = DangerRed)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { currentTab = GameSettingsScreen.Menu }) {
                                Text("Back", color = TextSecondary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    isUninstalling = true
                                    if (isCustom) {
                                        scope.launch(Dispatchers.IO) {
                                            val cm = ContainerManager(context)
                                            val sc = cm.loadShortcuts().find {
                                                it.getExtra("game_source") == "CUSTOM" &&
                                                    (it.getExtra("custom_name") == app.name || it.name == app.name)
                                            }
                                            sc?.file?.delete()
                                            val iconFile = java.io.File(
                                                context.filesDir,
                                                "custom_icons/${app.name.replace("/", "_")}.png",
                                            )
                                            iconFile.delete()
                                            PluviaApp.events.emit(
                                                AndroidEvent.LibraryInstallStatusChanged(app.id),
                                            )
                                            withContext(Dispatchers.Main) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "${app.name} removed.",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                                onDismissRequest()
                                            }
                                        }
                                    } else if (isEpic) {
                                        scope.launch(Dispatchers.IO) {
                                            val result = EpicService.deleteGame(context, epicId)
                                            withContext(Dispatchers.Main) {
                                                if (result.isSuccess) {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "${app.name} uninstalled.",
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                                } else {
                                                    val error =
                                                        result.exceptionOrNull()?.message ?: "Unknown error"
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Failed to uninstall: $error",
                                                        android.widget.Toast.LENGTH_LONG,
                                                    ).show()
                                                }
                                                onDismissRequest()
                                            }
                                        }
                                    } else {
                                        SteamService.uninstallApp(app.id) { success ->
                                            if (success) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "${app.name} uninstalled.",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            } else {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Failed to uninstall.",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                            onDismissRequest()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(if (isCustom) "Confirm Remove" else "Confirm Uninstall")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GOGGameSettingsDialog(app: GOGGame, onDismissRequest: () -> Unit) {
        val context = LocalContext.current
        var currentTab by remember { mutableStateOf(GameSettingsScreen.Menu) }
        val scope = rememberCoroutineScope()

        GameSettingsDialogFrame(
            title = app.title,
            subtitle = "GOG profile, shortcut tools, and cloud save actions.",
            sectionTitle = when (currentTab) {
                GameSettingsScreen.Menu -> "Actions"
                GameSettingsScreen.Saves -> "Cloud Saves"
                GameSettingsScreen.Uninstall -> "Uninstall Game"
            },
            onDismissRequest = onDismissRequest,
        ) {
            when (currentTab) {
                GameSettingsScreen.Menu -> {
                    GameSettingsActionGrid(
                        actions = listOf(
                            GameSettingsActionItem(
                                title = "Settings",
                                icon = Icons.Default.Settings,
                                onClick = {
                                    val shortcut = ContainerManager(context).loadShortcuts().find {
                                        it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == app.id
                                    }
                                    val intent = Intent(context, MainActivity::class.java)
                                    if (shortcut != null) {
                                        intent.putExtra("edit_shortcut_path", shortcut.file.absolutePath)
                                    } else {
                                        intent.putExtra("create_shortcut_for_gog_id", app.id)
                                        intent.putExtra("create_shortcut_for_app_id", gogPseudoId(app.id))
                                        intent.putExtra("create_shortcut_for_app_name", app.title)
                                    }
                                    intent.putExtra("return_to_unified", true)
                                    val opts = ActivityOptionsCompat.makeCustomAnimation(
                                        context,
                                        R.anim.settings_enter,
                                        R.anim.settings_exit,
                                    )
                                    context.startActivity(intent, opts.toBundle())
                                    onDismissRequest()
                                },
                            ),
                            GameSettingsActionItem(
                                title = "Shortcut",
                                icon = Icons.Default.Home,
                                onClick = {
                                    scope.launch {
                                        val artworkUrl = app.imageUrl.ifEmpty { app.iconUrl }
                                        val created = withContext(Dispatchers.IO) {
                                            addGogShortcutToHomeScreen(context, app, artworkUrl)
                                        }
                                        val message = if (created) {
                                            context.getString(R.string.library_shortcut_created)
                                        } else {
                                            context.getString(
                                                R.string.library_failed_to_create_shortcut,
                                                app.title,
                                            )
                                        }
                                        android.widget.Toast.makeText(
                                            context,
                                            message,
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                            ),
                            GameSettingsActionItem(
                                title = "Saves",
                                icon = Icons.Default.Save,
                                onClick = { currentTab = GameSettingsScreen.Saves },
                            ),
                            GameSettingsActionItem(
                                title = "Uninstall",
                                icon = Icons.Default.Delete,
                                accentColor = DangerRed,
                                onClick = { currentTab = GameSettingsScreen.Uninstall },
                            ),
                        ),
                    )
                }

                GameSettingsScreen.Saves -> {
                    GameSettingsActionGrid(
                        actions = listOf(
                            GameSettingsActionItem(
                                title = "Sync",
                                icon = Icons.Default.Cloud,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        GOGService.syncCloudSaves(context, "GOG_${app.id}", "auto")
                                    }
                                    android.widget.Toast.makeText(
                                        context,
                                        "Cloud sync started.",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ),
                            GameSettingsActionItem(
                                title = "Back",
                                icon = Icons.Default.ArrowBack,
                                onClick = { currentTab = GameSettingsScreen.Menu },
                            ),
                        ),
                    )
                }

                GameSettingsScreen.Uninstall -> {
                    var isUninstalling by remember { mutableStateOf(false) }

                    GameSettingsInfoCard(
                        message = "Uninstall ${app.title} and permanently delete its installed game folder.",
                        accentColor = DangerRed,
                    )

                    if (isUninstalling) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = DangerRed)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { currentTab = GameSettingsScreen.Menu }) {
                                Text("Back", color = TextSecondary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    isUninstalling = true
                                    scope.launch(Dispatchers.IO) {
                                        GOGService.deleteGame(
                                            context,
                                            LibraryItem(
                                                "GOG_${app.id}",
                                                app.title,
                                                com.winlator.cmod.steam.enums.GameSource.GOG,
                                            ),
                                        )
                                        withContext(Dispatchers.Main) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "${app.title} uninstalled.",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                            onDismissRequest()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Confirm Uninstall")
                            }
                        }
                    }
                }
            }
        }
    }

    // Single game capsule for carousel
    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    private fun GameCapsule(
        app: SteamApp,
        gogGame: GOGGame? = null,
        iconRefreshKey: Int = 0,
        isFocusedOverride: Boolean = false,
        shortcuts: List<Shortcut> = emptyList(),
        onLongClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val isCustom = app.id < 0
        val isEpic = app.id >= 2000000000
        val epicId = if (isEpic) app.id - 2000000000 else 0
        val epicGame by produceState<EpicGame?>(initialValue = null, key1 = epicId) {
            value = if (isEpic) db.epicGameDao().getById(epicId) else null
        }
        // Use pre-cached shortcuts list instead of loading from disk per-item
        val customLibraryIconPath = remember(app.id, gogGame?.id, iconRefreshKey, shortcuts) {
            val shortcut = if (gogGame != null) {
                shortcuts.find {
                    it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == gogGame.id
                }
            } else {
                findShortcutForGame(shortcuts, app, isCustom, isEpic, epicId)
            }
            val customPath = shortcut?.getExtra("customLibraryIconPath")
                ?.ifBlank { shortcut.getExtra("customCoverArtPath") }
            customPath?.takeIf { it.isNotBlank() && java.io.File(it).exists() }
        }
        val isFocused = isFocusedOverride

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, CardDark, RoundedCornerShape(12.dp))
                .chasingBorder(isFocused = isFocused, cornerRadius = 12.dp)
                .background(CardDark, RoundedCornerShape(12.dp))
                .focusable()
                .combinedClickable(
                    onClick = {
                        val containerManager = com.winlator.cmod.container.ContainerManager(context)
                        if (isCustom) {
                            launchCustomGame(context, containerManager, app.name)
                        } else if (gogGame != null) {
                            launchGogGame(context, containerManager, gogGame)
                        } else if (isEpic) {
                            epicGame?.let { launchEpicGame(context, containerManager, it) }
                        } else if (SteamService.isAppInstalled(app.id)) {
                            launchSteamGame(context, containerManager, app)
                        }
                    },
                    onLongClick = onLongClick
                )
        ) {
            // Art area — clip only on the image, not the text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                val artModifier = Modifier.fillMaxSize()
                val customArtworkFile = customLibraryIconPath
                    ?.let { java.io.File(it) }
                    ?.takeIf { it.exists() }

                if (customArtworkFile != null) {
                    val customArtworkCacheKey =
                        "library_custom_icon:${customArtworkFile.absolutePath}:${customArtworkFile.lastModified()}"
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(customArtworkFile)
                            .memoryCacheKey(customArtworkCacheKey)
                            .diskCacheKey(customArtworkCacheKey)
                            .crossfade(300)
                            .build(),
                        contentDescription = app.name,
                        modifier = artModifier,
                        contentScale = ContentScale.Crop
                    )
                } else if (isCustom) {
                    val safeName = app.name.replace("/", "_").replace("\\", "_")
                    val iconFile = java.io.File(context.filesDir, "custom_icons/$safeName.png")
                    if (iconFile.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(iconFile)
                                .crossfade(300)
                                .build(),
                            contentDescription = app.name,
                            modifier = artModifier,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = artModifier.background(SurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SportsEsports, contentDescription = app.name, tint = Accent.copy(alpha = 0.6f), modifier = Modifier.size(64.dp))
                        }
                    }
                } else if (gogGame != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(gogGame.imageUrl.ifEmpty { gogGame.iconUrl })
                            .crossfade(300)
                            .build(),
                        contentDescription = app.name,
                        modifier = artModifier,
                        contentScale = ContentScale.Crop
                    )
                } else if (isEpic) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(epicGame?.primaryImageUrl ?: epicGame?.iconUrl)
                            .crossfade(300)
                            .build(),
                        contentDescription = app.name,
                        modifier = artModifier,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val imageUrl = app.getCapsuleUrl()
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(300)
                            .build(),
                        contentDescription = app.name,
                        modifier = artModifier,
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Title below art, outside the clipped area
            Text(
                text = app.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .then(if (isFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }

    // Epic Store Tab
    @Composable
    fun EpicStoreTab(isLoggedIn: Boolean, searchQuery: String = "", onLoginClick: () -> Unit) {
        val context = LocalContext.current
        
        if (!isLoggedIn) {
            LoginRequiredScreen("Epic Games", onLoginClick)
            return
        }

        val epicApps by db.epicGameDao().getAll().collectAsState(initial = emptyList())
        val selectedAppId = remember { mutableStateOf<Int?>(null) }
        val gridState = rememberLazyGridState()
        val activity = LocalContext.current as? UnifiedActivity

        // Ensure library updates from cloud
        LaunchedEffect(Unit) {
            if (epicApps.isEmpty()) {
                EpicService.triggerLibrarySync(context)
            }
        }

        JoystickGridScroll(gridState, activity?.rightStickScrollState)

        val displayedApps = remember(epicApps, searchQuery) {
            if (searchQuery.isBlank()) epicApps
            else epicApps.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        FourByTwoGridView(
            items = displayedApps,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            gridState = gridState,
        ) { app, _, rowHeight ->
            Box(Modifier.height(rowHeight)) {
                EpicStoreCapsule(app) {
                    selectedAppId.value = app.id
                }
            }
        }

        val selectedApp = epicApps.find { it.id == selectedAppId.value }
        if (selectedApp != null) {
            EpicGameManagerDialog(
                app = selectedApp,
                onDismissRequest = { selectedAppId.value = null }
            )
        }
    }

    @Composable
    fun EpicStoreCapsule(app: com.winlator.cmod.epic.data.EpicGame, onClick: () -> Unit) {
        val context = LocalContext.current
        var isFocused by remember { mutableStateOf(false) }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, CardDark, RoundedCornerShape(16.dp))
                .chasingBorder(isFocused = isFocused, cornerRadius = 16.dp)
                .background(CardDark, RoundedCornerShape(16.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(app.primaryImageUrl).crossfade(300).build(),
                    contentDescription = app.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (app.installPath != null && java.io.File(app.installPath!!).exists()) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp).background(SurfaceDark.copy(alpha=0.7f), RoundedCornerShape(8.dp)).padding(4.dp)
                    ) {
                        Text("INSTALLED", color = StatusOnline, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                app.title,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp).fillMaxWidth()
                    .then(if (isFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    private fun StoreInstallDialogShell(
        title: String,
        heroImageUrl: String?,
        subtitle: String,
        onDismissRequest: () -> Unit,
        infoContent: @Composable ColumnScope.() -> Unit = {},
        actionsContent: @Composable ColumnScope.() -> Unit
    ) {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.864f).fillMaxHeight(0.92f),
                shape = RoundedCornerShape(20.dp),
                color = CardDark
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.42f)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(heroImageUrl)
                                    .crossfade(150)
                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = "$title artwork",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillWidth,
                                alignment = Alignment.TopCenter
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color.Transparent,
                                                0.45f to Color.Transparent,
                                                0.72f to CardDark.copy(alpha = 0.72f),
                                                1.0f to CardDark
                                            )
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 24.dp, end = 80.dp, bottom = 24.dp)
                            ) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (subtitle.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    "Installation Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    color = SurfaceDark,
                                    shape = RoundedCornerShape(16.dp),
                                    tonalElevation = 2.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(18.dp)
                                    ) {
                                        infoContent()
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .widthIn(min = 220.dp, max = 280.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)
                            ) {
                                actionsContent()
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(42.dp)
                            .shadow(8.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                            .clip(CircleShape)
                            .background(BgDark.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }
            }
        }
    }

    @Composable
    fun EpicGameManagerDialog(app: EpicGame, onDismissRequest: () -> Unit) {
        val context = LocalContext.current
        val installed = app.isInstalled && java.io.File(app.installPath).exists()
        val scope = rememberCoroutineScope()
        
        var isLoading by remember { mutableStateOf(!installed) }
        var manifestSizes by remember { mutableStateOf<EpicManager.ManifestSizes?>(null) }
        var dlcApps by remember { mutableStateOf<List<EpicGame>>(emptyList()) }
        val selectedDlcIds = remember { mutableStateListOf<Int>() }
        var customPath by remember { mutableStateOf<String?>(null) }
        var showCustomPathWarning by remember { mutableStateOf(false) }

        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri -> uri?.let { customPath = getPathFromTreeUri(it) } }

        if (showCustomPathWarning) {
            CustomPathWarningDialog(
                onDismiss = { showCustomPathWarning = false },
                onProceed = {
                    showCustomPathWarning = false
                    folderPickerLauncher.launch(null)
                }
            )
        }

        LaunchedEffect(app.id, installed) {
            if (!installed) {
                withContext(Dispatchers.IO) {
                    manifestSizes = EpicService.fetchManifestSizes(context, app.id)
                    dlcApps = EpicService.getDLCForGameSuspend(app.id)
                    isLoading = false
                }
            }
        }

        val totalInstallSize = manifestSizes?.installSize ?: 0L
        val totalDownloadSize = manifestSizes?.downloadSize ?: 0L
        val defaultPathSet = if (PrefManager.useSingleDownloadFolder) PrefManager.defaultDownloadFolder.isNotEmpty() else PrefManager.epicDownloadFolder.isNotEmpty()
        val effectivePath = customPath ?: EpicConstants.getGameInstallPath(context, app.appName)
        val availableBytes = try { StorageUtils.getAvailableSpace(effectivePath) } catch (e: Exception) { 0L }
        val isInstallEnabled = installed || availableBytes >= totalInstallSize
        val installPathDisplay = customPath ?: EpicConstants.defaultEpicGamesPath(context)

        StoreInstallDialogShell(
            title = app.title,
            heroImageUrl = app.artPortrait.ifEmpty { app.primaryImageUrl },
            subtitle = listOfNotNull(
                app.developer.takeIf { it.isNotBlank() },
                app.publisher.takeIf { it.isNotBlank() }
            ).joinToString(" • "),
            onDismissRequest = onDismissRequest,
            infoContent = {
                if (isLoading && !installed) {
                    Spacer(Modifier.height(18.dp))
                    CircularProgressIndicator(color = Accent)
                } else {
                    Text(
                        "Download: ${StorageUtils.formatBinarySize(totalDownloadSize)} • Install: ${StorageUtils.formatBinarySize(totalInstallSize)}",
                        color = TextPrimary
                    )
                    Text(
                        "Available: ${StorageUtils.formatBinarySize(availableBytes)}",
                        color = if (isInstallEnabled) TextSecondary else Color(0xFFFF6B6B)
                    )

                    if (dlcApps.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text("Add-ons / DLCs", color = TextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        dlcApps.forEach { dlc ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedDlcIds.contains(dlc.id)) selectedDlcIds.remove(dlc.id)
                                        else selectedDlcIds.add(dlc.id)
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = selectedDlcIds.contains(dlc.id),
                                    onCheckedChange = { if (it) selectedDlcIds.add(dlc.id) else selectedDlcIds.remove(dlc.id) }
                                )
                                Text(dlc.title, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        ) {
            if (installed) {
                if (app.cloudSaveEnabled) {
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                EpicCloudSavesManager.syncCloudSaves(context, app.id, "auto")
                            }
                            onDismissRequest()
                            android.widget.Toast.makeText(context, "Cloud sync started.", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Sync Cloud Saves", color = TextPrimary) }
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            EpicService.deleteGame(context, app.id)
                        }
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Uninstall", color = TextSecondary) }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        launchEpicGame(context, ContainerManager(context), app)
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Play Game") }
            } else {
                Text(
                    text = installPathDisplay,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    onClick = {
                        if (customPath == null && defaultPathSet) {
                            showCustomPathWarning = true
                        } else {
                            folderPickerLauncher.launch(null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (customPath != null) "Custom" else if (defaultPathSet) "Already Set" else "Custom", color = TextPrimary)
                }
                Button(
                    enabled = !isLoading && isInstallEnabled,
                    onClick = {
                        val installPath = if (customPath != null) {
                            val sanitizedTitle = app.title.replace(Regex("[^a-zA-Z0-9 \\-_]"), "").trim()
                            java.io.File(customPath!!, sanitizedTitle).absolutePath
                        } else {
                            EpicConstants.getGameInstallPath(context, app.title)
                        }
                        EpicService.downloadGame(context, app.id, selectedDlcIds.toList(), installPath, "en-US")
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (!isLoading && isInstallEnabled) Accent else Color.Gray),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Install") }
            }
        }
    }

    @Composable
    fun GOGStoreTab(isLoggedIn: Boolean, searchQuery: String = "", onLoginClick: () -> Unit) {
        if (!isLoggedIn) {
            LoginRequiredScreen("GOG", onLoginClick)
            return
        }

        val gogApps by db.gogGameDao().getAll().collectAsState(initial = emptyList())
        val selectedGameId = remember { mutableStateOf<String?>(null) }
        val gridState = rememberLazyGridState()

        val displayedApps = remember(gogApps, searchQuery) {
            if (searchQuery.isBlank()) gogApps
            else gogApps.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        FourByTwoGridView(
            items = displayedApps,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            gridState = gridState,
        ) { app, _, rowHeight ->
            val isInstalled = app.isInstalled && java.io.File(app.installPath).exists()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .border(1.dp, CardDark, RoundedCornerShape(16.dp))
                    .background(CardDark, RoundedCornerShape(16.dp))
                    .clickable { selectedGameId.value = app.id }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(app.imageUrl.ifEmpty { app.iconUrl })
                            .crossfade(300)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isInstalled) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Installed",
                            tint = StatusOnline,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(24.dp)
                        )
                    }
                }

                Text(
                    text = app.title,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        selectedGameId.value?.let { gameId ->
            val app = gogApps.firstOrNull { it.id == gameId }
            if (app != null) {
                GOGGameManagerDialog(app = app) { selectedGameId.value = null }
            }
        }
    }

    @Composable
    fun GOGGameManagerDialog(app: GOGGame, onDismissRequest: () -> Unit) {
        val context = LocalContext.current
        val installed = app.isInstalled && java.io.File(app.installPath).exists()
        val scope = rememberCoroutineScope()
        var customPath by remember { mutableStateOf<String?>(null) }
        var showCustomPathWarning by remember { mutableStateOf(false) }

        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri -> uri?.let { customPath = getPathFromTreeUri(it) } }

        if (showCustomPathWarning) {
            CustomPathWarningDialog(
                onDismiss = { showCustomPathWarning = false },
                onProceed = {
                    showCustomPathWarning = false
                    folderPickerLauncher.launch(null)
                }
            )
        }

        val defaultPathSet = if (PrefManager.useSingleDownloadFolder) PrefManager.defaultDownloadFolder.isNotEmpty() else PrefManager.gogDownloadFolder.isNotEmpty()
        val installRootPath = customPath ?: GOGConstants.defaultGOGGamesPath
        val installPathDisplay = if (customPath != null) {
            java.io.File(customPath!!, GOGConstants.getSanitizedGameFolderName(app.title)).absolutePath
        } else {
            GOGConstants.getGameInstallPath(app.title)
        }
        val requiredBytes = maxOf(app.installSize, app.downloadSize)
        val availableBytes = try { StorageUtils.getAvailableSpace(installRootPath) } catch (_: Exception) { 0L }
        val isInstallEnabled = installed || availableBytes >= requiredBytes

        StoreInstallDialogShell(
            title = app.title,
            heroImageUrl = app.imageUrl.ifEmpty { app.iconUrl },
            subtitle = listOfNotNull(
                app.developer.takeIf { it.isNotBlank() },
                app.publisher.takeIf { it.isNotBlank() }
            ).joinToString(" • "),
            onDismissRequest = onDismissRequest,
            infoContent = {
                Text(
                    "Download: ${StorageUtils.formatBinarySize(app.downloadSize)} • Install: ${StorageUtils.formatBinarySize(app.installSize)}",
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Available: ${StorageUtils.formatBinarySize(availableBytes)}",
                    color = if (isInstallEnabled) TextSecondary else Color(0xFFFF6B6B)
                )
            }
        ) {
            if (installed) {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            GOGService.syncCloudSaves(context, "GOG_${app.id}", "auto")
                        }
                        onDismissRequest()
                        android.widget.Toast.makeText(context, "Cloud sync started.", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Sync Cloud Saves", color = TextPrimary) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        launchGogGame(context, ContainerManager(context), app)
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Play Game", color = TextSecondary) }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            GOGService.deleteGame(context, LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.steam.enums.GameSource.GOG))
                            withContext(Dispatchers.Main) { onDismissRequest() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Uninstall") }
            } else {
                Text(
                    text = installPathDisplay,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    onClick = {
                        if (customPath == null && defaultPathSet) {
                            showCustomPathWarning = true
                        } else {
                            folderPickerLauncher.launch(null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (customPath != null) "Custom" else if (defaultPathSet) "Already Set" else "Custom", color = TextPrimary)
                }
                Button(
                    enabled = isInstallEnabled,
                    onClick = {
                        GOGService.downloadGame(context, app.id, installPathDisplay, PrefManager.containerLanguage)
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isInstallEnabled) Accent else Color.Gray),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Install") }
            }
        }
    }

    // Steam Store Tab
    @Composable
    fun SteamStoreTab(isLoggedIn: Boolean, steamApps: List<SteamApp>, searchQuery: String = "") {
        if (!isLoggedIn) {
            LoginRequiredScreen("Steam") {
                startActivity(Intent(this@UnifiedActivity, SteamLoginActivity::class.java))
            }
            return
        }

        var selectedAppForDialog by remember { mutableStateOf<SteamApp?>(null) }
        val gridState = rememberLazyGridState()
        val activity = LocalContext.current as? UnifiedActivity

        // Right joystick: 2x faster at full push with quadratic speed curve
        JoystickGridScroll(gridState, activity?.rightStickScrollState, minSpeed = 2.5f, maxSpeed = 16f, quadratic = true)
        // Left joystick: 75% slower scrolling (vertical only, for browsing store)
        JoystickGridScroll(gridState, activity?.leftStickScrollState, deadZone = 0.15f, minSpeed = 0.3125f, maxSpeed = 2f)

        val displayedApps = remember(steamApps, searchQuery) {
            if (searchQuery.isBlank()) steamApps
            else steamApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        FourByTwoGridView(
            items = displayedApps,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            gridState = gridState,
        ) { app, _, rowHeight ->
            Box(Modifier.height(rowHeight)) {
                SteamStoreCapsule(app, onClick = { selectedAppForDialog = app })
            }
        }

        if (selectedAppForDialog != null) {
            GameManagerDialog(
                app = selectedAppForDialog!!,
                onDismissRequest = { selectedAppForDialog = null }
            )
        }
    }

    @Composable
    fun SteamStoreCapsule(app: SteamApp, onClick: () -> Unit) {
        val isInstalled = SteamService.isAppInstalled(app.id)
        val context = LocalContext.current
        var isFocused by remember { mutableStateOf(false) }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, CardDark, RoundedCornerShape(16.dp))
                .chasingBorder(isFocused = isFocused, cornerRadius = 16.dp)
                .background(CardDark, RoundedCornerShape(16.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                val imageUrl = app.getCapsuleUrl()

                AsyncImage(
                    model = ImageRequest.Builder(context).data(imageUrl).crossfade(300).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isInstalled) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Installed",
                        tint = StatusOnline,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(24.dp)
                    )
                }
            }

            Text(
                text = app.name,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                    .then(if (isFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }

    // Downloads Tab
    @Composable
    fun DownloadsTab(selectedId: String?, onSelectDownload: (String?) -> Unit) {
        val downloads = remember { mutableStateListOf<Pair<String, DownloadInfo>>() }
        // Tick counter forces recomposition so button labels/states refresh with status changes
        var tick by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            while (true) {
                val currentDownloads = DownloadService.getAllDownloads()
                downloads.clear()
                downloads.addAll(currentDownloads)
                if (selectedId != null && currentDownloads.none { it.first == selectedId }) {
                    onSelectDownload(null)
                }
                tick++
                kotlinx.coroutines.delay(1000)
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val isController = ControllerHelper.isControllerConnected()
            val isPS = ControllerHelper.isPlayStationController()

            // Read tick to ensure recomposition picks up latest status values
            @Suppress("UNUSED_EXPRESSION")
            tick

            // Global Actions row
            val buttonHeight = 40.dp
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val selectedInfo = downloads.find { it.first == selectedId }?.second
                val selectedStatus = selectedInfo?.getStatusFlow()?.value
                val isPaused = selectedStatus == DownloadPhase.PAUSED
                val isComplete = selectedStatus == DownloadPhase.COMPLETE
                val isCancelled = selectedStatus == DownloadPhase.CANCELLED
                val pausableDownloads = downloads.filter {
                    val status = it.second.getStatusFlow().value
                    status != DownloadPhase.COMPLETE && status != DownloadPhase.CANCELLED
                }
                val allPausableDownloadsPaused = pausableDownloads.isNotEmpty() && pausableDownloads.all {
                    it.second.getStatusFlow().value == DownloadPhase.PAUSED
                }

                val pauseResumeLabel = if (selectedId == null) {
                    if (allPausableDownloadsPaused) "Resume All" else "Pause All"
                } else {
                    if (isPaused) "Resume" else "Pause"
                }

                val cancelLabel = if (selectedId == null) "Cancel All" else "Cancel"

                // Disable pause/resume for completed or cancelled downloads
                val pauseResumeEnabled = if (selectedId != null) {
                    !isComplete && !isCancelled
                } else {
                    pausableDownloads.isNotEmpty()
                }

                val cancelEnabled = if (selectedId != null) {
                    !isComplete && !isCancelled
                } else {
                    pausableDownloads.isNotEmpty()
                }

                // Download Queue Size
                var queueSize by remember { mutableIntStateOf(PrefManager.downloadQueueSize) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(buttonHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = { if (queueSize > 1) { queueSize--; PrefManager.downloadQueueSize = queueSize } },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Decrease Queue", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = queueSize.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    IconButton(
                        onClick = { queueSize++; PrefManager.downloadQueueSize = queueSize },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Increase Queue", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        if (selectedId == null) {
                            if (allPausableDownloadsPaused) {
                                DownloadService.resumeAll()
                            } else {
                                DownloadService.pauseAll()
                            }
                        } else {
                            if (isPaused) {
                                DownloadService.resumeDownload(selectedId)
                            } else {
                                DownloadService.pauseDownload(selectedId)
                            }
                        }
                    },
                    enabled = pauseResumeEnabled,
                    modifier = Modifier.height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pauseResumeLabel, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isController) {
                            Spacer(Modifier.width(8.dp))
                            ControllerBadge(if (isPS) "L2" else "LT")
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        if (selectedId == null) {
                            DownloadService.cancelAll()
                            onSelectDownload(null)
                        } else {
                            DownloadService.cancelDownload(selectedId)
                            onSelectDownload(null)
                        }
                    },
                    enabled = cancelEnabled,
                    modifier = Modifier.height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cancelLabel, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isController) {
                            Spacer(Modifier.width(8.dp))
                            ControllerBadge(if (isPS) "R2" else "RT")
                        }
                    }
                }

                // Clear button - clears completed and cancelled downloads
                val hasCompletedOrCancelled = downloads.any {
                    val s = it.second.getStatusFlow().value
                    s == DownloadPhase.COMPLETE || s == DownloadPhase.CANCELLED
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        DownloadService.clearCompletedDownloads()
                    },
                    enabled = hasCompletedOrCancelled,
                    modifier = Modifier.height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Clear", color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            val listState = rememberLazyListState()
            val activity = LocalContext.current as? UnifiedActivity
            val density = LocalContext.current.resources.displayMetrics.density
            
            LaunchedEffect(listState) {
                activity?.rightStickScrollState?.collect { rz ->
                    if (kotlin.math.abs(rz) > 0.1f) {
                        // Max scroll speed is 20 rows per second (approx 20 * 100dp / 60fps ~ 32dp per frame)
                        // Min scroll speed is 0.75 rows per second (approx 0.75 * 100dp / 60fps ~ 1.25dp per frame)
                        // Use a square curve for more gradual acceleration
                        val speedFactor = kotlin.math.abs(rz)
                        val curveFactor = speedFactor * speedFactor
                        val baseSpeed = 1.25f + (curveFactor * (32f - 1.25f))
                        val direction = if (rz > 0) 1f else -1f
                        
                        // Using a loop while the stick is held
                        while(kotlin.math.abs(activity.rightStickScrollState.value) > 0.1f) {
                            val currentRz = activity.rightStickScrollState.value
                            val currentSpeedFactor = kotlin.math.abs(currentRz)
                            val currentCurveFactor = currentSpeedFactor * currentSpeedFactor
                            val currentBaseSpeed = 1.25f + (currentCurveFactor * (32f - 1.25f))
                            val currentDirection = if (currentRz > 0) 1f else -1f
                            
                            val pixelsToScroll = currentBaseSpeed * currentDirection * density
                            listState.dispatchRawDelta(pixelsToScroll)
                            kotlinx.coroutines.delay(16) // roughly 60fps
                        }
                    }
                }
            }

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(downloads) { (id, info) ->
                    DownloadItemDeck(id, info, isSelected = selectedId == id, onClick = {
                        if (selectedId == id) onSelectDownload(null) else onSelectDownload(id)
                    })
                }
                if (downloads.isEmpty()) {
                    item { EmptyStateMessage("No active downloads.") }
                }
            }
        }
    }

    @Composable
    fun DownloadItemDeck(id: String, info: DownloadInfo, isSelected: Boolean, onClick: () -> Unit) {
        var progress by remember { mutableFloatStateOf(info.getProgress()) }
        var showDeleteDialog by remember { mutableStateOf(false) }

        DisposableEffect(info) {
            val listener: (Float) -> Unit = { progress = it }
            info.addProgressListener(listener)
            onDispose { info.removeProgressListener(listener) }
        }
        val status by info.getStatusFlow().collectAsState()
        val statusMessage by info.getStatusMessageFlow().collectAsState()
        val isSteam = id.startsWith("STEAM_")
        val isEpic = id.startsWith("EPIC_")
        val isGog = id.startsWith("GOG_")
        val appId = if (isSteam) id.removePrefix("STEAM_").toIntOrNull() ?: 0 
                    else if (isEpic) id.removePrefix("EPIC_").toIntOrNull() ?: 0
                    else 0
        val gogId = if (isGog) id.removePrefix("GOG_") else ""
                    
        var steamApp by remember(appId) { mutableStateOf<SteamApp?>(null) }
        var epicGame by remember(appId) { mutableStateOf<EpicGame?>(null) }
        var gogGame by remember(gogId) { mutableStateOf<GOGGame?>(null) }
        val context = LocalContext.current
        var isFocused by remember { mutableStateOf(false) }
        val borderColor = if (isFocused || isSelected) Accent.copy(alpha = 0.8f) else Color.Transparent

        LaunchedEffect(appId, gogId, isSteam, isEpic, isGog) {
            withContext(Dispatchers.IO) { 
                if (isSteam) steamApp = db.steamAppDao().findApp(appId)
                else if (isEpic) epicGame = EpicService.getEpicGameOf(appId)
                else if (isGog) gogGame = GOGService.getGOGGameOf(gogId)
            }
        }

        val displayName = if (isSteam) steamApp?.name else if (isEpic) epicGame?.title else if (isGog) gogGame?.title else "Unknown Game"
        val displayImage = if (isSteam) steamApp?.getHeaderImageUrl()
                           else if (isEpic) epicGame?.primaryImageUrl ?: epicGame?.iconUrl
                           else if (isGog) gogGame?.imageUrl ?: gogGame?.iconUrl
                           else null

        Surface(
            color = if (isSelected) SurfaceDark else CardDark,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(4.dp, borderColor, RoundedCornerShape(12.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(displayImage)
                        .crossfade(300).build(),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    val currentFile by info.getCurrentFileNameFlow().collectAsState()
                    val (downloadedBytes, totalBytes) = info.getBytesProgress()
                    val speed = info.getCurrentDownloadSpeed() ?: 0L
                    val percentage = (progress * 100).toInt()

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(displayName ?: "Unknown Game", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        
                        // Centered Size Info
                        Text(
                            text = "${StorageUtils.formatBinarySize(downloadedBytes)} / ${StorageUtils.formatBinarySize(totalBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            if (status == DownloadPhase.DOWNLOADING && speed > 0) {
                                Text(
                                    text = "${StorageUtils.formatBinarySize(speed)}/s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    val statusText = when (status) {
                        DownloadPhase.DOWNLOADING -> {
                            val filePart = currentFile?.let { " [${it.take(10)}]" } ?: ""
                            "Downloading...$filePart"
                        }
                        DownloadPhase.PAUSED -> "Paused"
                        DownloadPhase.PREPARING -> "Preparing..."
                        DownloadPhase.VERIFYING -> {
                            val filePart = currentFile?.let { " [${it.take(10)}]" } ?: ""
                            "Verifying...$filePart"
                        }
                        DownloadPhase.PATCHING -> "Patching..."
                        DownloadPhase.COMPLETE -> "Complete"
                        DownloadPhase.CANCELLED -> "Cancelled"
                        DownloadPhase.FAILED -> "Failed: ${if (statusMessage != null && statusMessage != "null") statusMessage else "Unknown error"}"
                        else -> status.name.lowercase().replaceFirstChar { it.uppercase() }
                    }

                    Text("Status: $statusText", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
                            color = when (status) {
                                DownloadPhase.FAILED -> Color(0xFFFF6B6B)
                                DownloadPhase.CANCELLED -> Color(0xFFFF6B6B)
                                DownloadPhase.COMPLETE -> Color(0xFF4CAF50)
                                else -> Accent
                            },
                            trackColor = Color.Black.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { showDeleteDialog = true },
                    enabled = status != DownloadPhase.COMPLETE && status != DownloadPhase.CANCELLED
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel Download",
                        tint = if (status != DownloadPhase.COMPLETE && status != DownloadPhase.CANCELLED) Color(0xFFFF6B6B) else TextSecondary
                    )
                }
                if (ControllerHelper.isControllerConnected()) {
                    Spacer(Modifier.width(8.dp))
                    ControllerBadge(if (ControllerHelper.isPlayStationController()) "Cross" else "A")
                }
            }
        }

        if (showDeleteDialog) {
            val gameName = if (id.startsWith("STEAM_")) steamApp?.name
                else if (id.startsWith("EPIC_")) epicGame?.title
                else if (id.startsWith("GOG_")) gogGame?.title
                else null
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = SurfaceDark,
                title = { Text("Cancel Download", color = TextPrimary) },
                text = { Text("Cancel the download for ${gameName ?: "this game"} and delete all downloaded files?", color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            DownloadService.cancelDownload(id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                    ) {
                        Text("Cancel Download", color = Color.White)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showDeleteDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CardDark)
                    ) {
                        Text("Cancel", color = TextPrimary)
                    }
                }
            )
        }
    }

    // Store placeholder tabs
    @Composable
    fun StorePlaceholderTab(storeName: String) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Store,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("$storeName", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Coming soon", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { /* TODO: Wire sign-in flow */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign In to $storeName")
                }
            }
        }
    }

    // Game Manager Dialog
    @Composable
    fun GameManagerDialog(app: SteamApp, onDismissRequest: () -> Unit) {
        val context = LocalContext.current
        var isLoading by remember { mutableStateOf(true) }
        var depots by remember { mutableStateOf<Map<Int, DepotInfo>>(emptyMap()) }
        var dlcApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        val selectedDlcIds = remember { mutableStateListOf<Int>() }
        var customPath by remember { mutableStateOf<String?>(null) }
        var showCustomPathWarning by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri -> uri?.let { customPath = getPathFromTreeUri(it) } }

        if (showCustomPathWarning) {
            CustomPathWarningDialog(
                onDismiss = { showCustomPathWarning = false },
                onProceed = {
                    showCustomPathWarning = false
                    folderPickerLauncher.launch(null)
                }
            )
        }

        LaunchedEffect(app.id) {
            withContext(Dispatchers.IO) {
                depots = SteamService.getDownloadableDepots(app.id)
                dlcApps = db.steamAppDao().findDownloadableDLCApps(app.id) ?: emptyList()
                isLoading = false
            }
        }

        val totalInstallSize = depots.values.sumOf { it.manifests["public"]?.size ?: 0L }
        val totalDownloadSize = depots.values.sumOf { it.manifests["public"]?.download ?: 0L }
        val defaultPathSet = if (PrefManager.useSingleDownloadFolder) PrefManager.defaultDownloadFolder.isNotEmpty() else PrefManager.steamDownloadFolder.isNotEmpty()
        val effectivePath = customPath ?: SteamService.defaultAppInstallPath
        val availableBytes = try { StorageUtils.getAvailableSpace(effectivePath) } catch (e: Exception) { 0L }
        val isInstallEnabled = availableBytes >= totalInstallSize
        val installPathDisplay = customPath ?: SteamService.defaultAppInstallPath

        StoreInstallDialogShell(
            title = app.name,
            heroImageUrl = app.getHeroUrl(),
            subtitle = listOfNotNull(
                app.developer.takeIf { it.isNotBlank() },
                app.publisher.takeIf { it.isNotBlank() }
            ).joinToString(" • "),
            onDismissRequest = onDismissRequest,
            infoContent = {
                if (isLoading) {
                    Spacer(Modifier.height(18.dp))
                    CircularProgressIndicator(color = Accent)
                } else {
                    Text(
                        "Download: ${StorageUtils.formatBinarySize(totalDownloadSize)} • Install: ${StorageUtils.formatBinarySize(totalInstallSize)}",
                        color = TextPrimary
                    )
                    Text(
                        "Available: ${StorageUtils.formatBinarySize(availableBytes)}",
                        color = if (isInstallEnabled) TextSecondary else Color(0xFFFF6B6B)
                    )

                    if (dlcApps.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text("DLCs Available", color = TextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        dlcApps.forEach { dlc ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedDlcIds.contains(dlc.id)) selectedDlcIds.remove(dlc.id)
                                        else selectedDlcIds.add(dlc.id)
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = selectedDlcIds.contains(dlc.id),
                                    onCheckedChange = { if (it) selectedDlcIds.add(dlc.id) else selectedDlcIds.remove(dlc.id) }
                                )
                                Text(dlc.name, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        ) {
            Text(
                text = installPathDisplay,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = {
                    if (customPath == null && defaultPathSet) {
                        showCustomPathWarning = true
                    } else {
                        folderPickerLauncher.launch(null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (customPath != null) "Custom" else if (defaultPathSet) "Already Set" else "Custom", color = TextPrimary)
            }
            Button(
                enabled = !isLoading && isInstallEnabled,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        SteamService.downloadApp(app.id, selectedDlcIds.toList(), false, customPath)
                        withContext(Dispatchers.Main) { onDismissRequest() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (!isLoading && isInstallEnabled) Accent else Color.Gray),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Install") }
        }
    }

    private fun getPathFromTreeUri(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            if (docId.startsWith("primary:")) {
                val path = docId.substringAfter(":")
                val externalStorage = android.os.Environment.getExternalStorageDirectory()
                if (path.isEmpty()) externalStorage.path else "${externalStorage.path}/$path"
            } else if (docId.contains(":")) {
                val parts = docId.split(":", limit = 2)
                if (parts.size == 2) {
                    val volumeId = parts[0]
                    val path = parts[1]
                    if (path.isEmpty()) "/storage/$volumeId" else "/storage/$volumeId/$path"
                } else null
            } else docId
        } catch (e: Exception) { uri.path }
    }

    private fun findLibraryShortcutForGame(
        containerManager: ContainerManager,
        app: SteamApp,
        isCustom: Boolean,
        isEpic: Boolean,
        epicId: Int
    ): Shortcut? {
        return findShortcutForGame(containerManager.loadShortcuts(), app, isCustom, isEpic, epicId)
    }

    private fun findShortcutForGame(
        shortcuts: List<Shortcut>,
        app: SteamApp,
        isCustom: Boolean,
        isEpic: Boolean,
        epicId: Int
    ): Shortcut? {
        return when {
            isCustom -> shortcuts.find {
                it.getExtra("game_source") == "CUSTOM" && (it.getExtra("custom_name") == app.name || it.name == app.name)
            }
            isEpic -> shortcuts.find {
                it.getExtra("game_source") == "EPIC" && it.getExtra("app_id") == epicId.toString()
            }
            else -> shortcuts.find {
                it.getExtra("app_id") == app.id.toString()
            }
        }
    }

    private fun resolveLibraryShortcutArtworkModel(
        context: android.content.Context,
        app: SteamApp,
        isCustom: Boolean,
        isEpic: Boolean,
        epicArtworkUrl: String?
    ): Any? {
        return when {
            isCustom -> {
                val safeName = app.name.replace("/", "_").replace("\\", "_")
                val iconFile = java.io.File(context.filesDir, "custom_icons/$safeName.png")
                if (iconFile.exists()) iconFile else null
            }
            isEpic -> epicArtworkUrl?.takeIf { it.isNotBlank() }
            else -> app.getCapsuleUrl()
        }
    }

    private suspend fun loadArtworkBitmap(context: android.content.Context, artworkModel: Any?): Bitmap? {
        if (artworkModel == null) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(artworkModel)
                .allowHardware(false)
                .size(192, 192)
                .build()
            val result = context.imageLoader.execute(request)
            val drawable = result.drawable ?: return null
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 192
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 192
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun requestPinnedHomeShortcut(
        context: android.content.Context,
        shortcut: Shortcut,
        artworkModel: Any? = null
    ): Boolean {
        if (shortcut.getExtra("uuid").isEmpty()) {
            shortcut.genUUID()
        }
        val shortcutId = shortcut.getExtra("uuid")
        if (shortcutId.isEmpty()) return false
        val canonicalShortcutPath = shortcut.file.absolutePath
        val shortcutPathHash = canonicalShortcutPath.hashCode()
        val pinShortcutId = "shortcut_${shortcut.container.id}_${shortcutId}_${shortcutPathHash.toUInt().toString(16)}"

        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java) ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) return false

        val launchIntent = Intent(context, XServerDisplayActivity::class.java).apply {
            val containerIdForLaunch = shortcut.getExtra("container_id").toIntOrNull() ?: shortcut.container.id
            val launchData = Uri.Builder()
                .scheme("winnative")
                .authority(BuildConfig.APPLICATION_ID)
                .appendPath("shortcut")
                .appendQueryParameter("uuid", shortcutId)
                .appendQueryParameter("container", containerIdForLaunch.toString())
                .appendQueryParameter("hash", shortcutPathHash.toString())
                .build()
            action = Intent.ACTION_VIEW
            data = launchData
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("container_id", containerIdForLaunch)
            putExtra("shortcut_path", canonicalShortcutPath)
            putExtra("shortcut_name", shortcut.name)
            putExtra("shortcut_uuid", shortcutId)
            putExtra("shortcut_path_hash", shortcutPathHash)
        }

        val customIconPath = shortcut.getExtra("customLibraryIconPath")
            .ifBlank { shortcut.getExtra("customCoverArtPath") }
        val customArtworkModel = customIconPath
            .takeIf { it.isNotBlank() }
            ?.let { java.io.File(it) }
            ?.takeIf { it.exists() }

        val artworkBitmap = loadArtworkBitmap(context, customArtworkModel) ?: loadArtworkBitmap(context, artworkModel)
        val shortcutIcon = artworkBitmap?.let { android.graphics.drawable.Icon.createWithBitmap(it) }
            ?: shortcut.icon?.let { android.graphics.drawable.Icon.createWithBitmap(it) }
            ?: android.graphics.drawable.Icon.createWithResource(context, R.drawable.icon_shortcut)

        val pinShortcutInfo = android.content.pm.ShortcutInfo.Builder(context, pinShortcutId)
            .setShortLabel(shortcut.name)
            .setLongLabel(shortcut.name)
            .setIcon(shortcutIcon)
            .setIntent(launchIntent)
            .build()

        return shortcutManager.requestPinShortcut(pinShortcutInfo, null)
    }

    private suspend fun addLibraryShortcutToHomeScreen(
        context: android.content.Context,
        app: SteamApp,
        isCustom: Boolean,
        isEpic: Boolean,
        epicId: Int,
        epicArtworkUrl: String? = null
    ): Boolean {
        val containerManager = ContainerManager(context)
        val shortcut = findLibraryShortcutForGame(containerManager, app, isCustom, isEpic, epicId) ?: return false
        val artworkModel = resolveLibraryShortcutArtworkModel(context, app, isCustom, isEpic, epicArtworkUrl)
        return requestPinnedHomeShortcut(context, shortcut, artworkModel)
    }

    private suspend fun addGogShortcutToHomeScreen(
        context: android.content.Context,
        app: GOGGame,
        artworkUrl: String?
    ): Boolean {
        val shortcut = ContainerManager(context).loadShortcuts().find {
            it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == app.id
        } ?: return false
        val artworkModel = artworkUrl?.takeIf { it.isNotBlank() }
        return requestPinnedHomeShortcut(context, shortcut, artworkModel)
    }

    // Game launch with A: drive mounting
    private fun launchSteamGame(context: android.content.Context, containerManager: ContainerManager, app: SteamApp) {
        val gameInstallPath = SteamService.getAppDirPath(app.id)
        val gameDir = java.io.File(gameInstallPath)
        if (!gameDir.exists()) {
            android.widget.Toast.makeText(context, "Game not installed: ${app.name}", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Try to find an existing shortcut first
        var shortcut = containerManager.loadShortcuts().find {
            it.getExtra("app_id") == app.id.toString()
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            // Initiate Cloud Sync download
            val prefixToPath: (String) -> String = { prefix ->
                com.winlator.cmod.steam.enums.PathType.from(prefix).toAbsPath(context, app.id, SteamService.userSteamId?.accountID ?: 0L)
            }
            SteamService.beginLaunchApp(
                appId = app.id,
                prefixToPath = prefixToPath,
                ignorePendingOperations = true,
                preferredSave = com.winlator.cmod.steam.enums.SaveLocation.None,
            ).await()

            if (shortcut != null) {
                // Existing shortcut: mount A: drive to game install path on its container
                mountADrive(shortcut!!.container, gameInstallPath)
                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", shortcut!!.container.id)
                intent.putExtra("shortcut_path", shortcut!!.file.path)
                intent.putExtra("shortcut_name", shortcut!!.name)
                context.startActivity(intent)
            } else {
                // No shortcut — get or auto-create a container 
                var containers = containerManager.getContainers()
                var container = containers.firstOrNull()
                if (container == null) {
                    // Auto-create a default container using the preferred wine version
                    try {
                        val data = org.json.JSONObject()
                        data.put("name", "Default")
                        // Use the wine version of the first existing container if any, 
                        // otherwise fall back to the default
                        val existingContainers = containerManager.containers
                        val defaultWineVersion = if (existingContainers.isNotEmpty()) {
                            existingContainers[0].wineVersion
                        } else {
                            com.winlator.cmod.core.WineInfo.MAIN_WINE_VERSION.identifier()
                        }
                        data.put("wineVersion", defaultWineVersion)
                        val contentsManager = com.winlator.cmod.contents.ContentsManager(context)
                        contentsManager.syncContents()
                        container = containerManager.createContainer(data, contentsManager)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (container == null) {
                    android.widget.Toast.makeText(context, "Failed to create container. Open Game Settings first.", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }

                mountADrive(container, gameInstallPath)

                // Find the first .exe in the game directory
                val exeFile = findGameExe(gameDir)
                val execPath = if (exeFile != null) {
                    val dosPath = exeFile.relativeTo(gameDir).path.replace("/", "\\\\")
                    "wine \"A:\\\\${dosPath}\""
                } else {
                    "wine \"C:\\\\Program Files (x86)\\\\Steam\\\\steamclient_loader_x64.exe\""
                }

                // Generate a shortcut dynamically
                val desktopDir = container.getDesktopDir()
                if (!desktopDir.exists()) desktopDir.mkdirs()
                val shortcutFile = java.io.File(desktopDir, "${app.name.replace("/", "_")}.desktop")
                val content = java.lang.StringBuilder()
                content.append("[Desktop Entry]\n")
                content.append("Type=Application\n")
                content.append("Name=${app.name}\n")
                content.append("Exec=$execPath\n")
                content.append("Icon=steam_icon_${app.id}\n")
                content.append("\n[Extra Data]\n")
                content.append("game_source=STEAM\n")
                content.append("app_id=${app.id}\n")
                content.append("container_id=${container.id}\n")
                content.append("game_install_path=${gameInstallPath}\n")

                com.winlator.cmod.core.FileUtils.writeString(shortcutFile, content.toString())

                container.saveData()

                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", container.id)
                intent.putExtra("shortcut_path", shortcutFile.path)
                intent.putExtra("shortcut_name", app.name)
                context.startActivity(intent)
            }
        }
    }

    private fun launchEpicGame(context: android.content.Context, containerManager: ContainerManager, app: EpicGame) {
        val gameInstallPath = app.installPath.takeIf { it.isNotEmpty() } ?: EpicConstants.getGameInstallPath(context, app.appName)
        val gameDir = java.io.File(gameInstallPath)
        if (!gameDir.exists()) {
            android.widget.Toast.makeText(context, "Game not installed: ${app.title}", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Try to find an existing shortcut first (preserves per-game settings)
        var existingShortcut = containerManager.loadShortcuts().find {
            it.getExtra("game_source") == "EPIC" && it.getExtra("app_id") == app.id.toString()
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val launchArgsResult = withContext(kotlinx.coroutines.Dispatchers.IO) {
                EpicGameLauncher.buildLaunchParameters(context, app)
            }
            val args = launchArgsResult.getOrNull()?.joinToString(" ") ?: ""

            if (existingShortcut != null) {
                // Existing shortcut found: preserve per-game settings, just update install path and mount A: drive
                val shortcut = existingShortcut!!
                // Ensure game_install_path is always up-to-date
                shortcut.putExtra("game_install_path", gameInstallPath)

                // Repair broken Exec line if exe path is missing (just "A:\")
                val currentPath = shortcut.path
                if (currentPath == null || currentPath == "A:\\" || currentPath == "A:\\\\") {
                    var exePath = withContext(kotlinx.coroutines.Dispatchers.IO) { EpicService.getInstalledExe(app.id) }
                    val newExecCmd = if (exePath.isNotEmpty()) {
                        "wine \"A:\\\\${exePath.replace("/", "\\\\")}\""
                    } else {
                        val exeFile = findGameExe(gameDir)
                        if (exeFile != null) {
                            val dosPath = exeFile.relativeTo(gameDir).path.replace("/", "\\\\")
                            "wine \"A:\\\\${dosPath}\""
                        } else null
                    }
                    if (newExecCmd != null) {
                        // Rewrite the Exec line in the .desktop file while preserving all other content
                        val lines = com.winlator.cmod.core.FileUtils.readLines(shortcut.file)
                        val sb = StringBuilder()
                        for (line in lines) {
                            if (line.startsWith("Exec=")) {
                                sb.append("Exec=$newExecCmd\n")
                            } else {
                                sb.append(line).append("\n")
                            }
                        }
                        com.winlator.cmod.core.FileUtils.writeString(shortcut.file, sb.toString())
                    }
                }

                shortcut.saveData()

                mountADrive(shortcut.container, gameInstallPath)

                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", shortcut.container.id)
                intent.putExtra("shortcut_path", shortcut.file.path)
                intent.putExtra("shortcut_name", shortcut.name)
                intent.putExtra("extra_exec_args", args) // Pass fresh tokens
                context.startActivity(intent)
            } else {
                // No existing shortcut — create a new one
                var exePath = withContext(kotlinx.coroutines.Dispatchers.IO) { EpicService.getInstalledExe(app.id) }
                val execCmd = if (exePath.isNotEmpty()) {
                    "wine \"A:\\\\${exePath.replace("/", "\\\\")}\""
                } else {
                    val exeFile = findGameExe(gameDir)
                    if (exeFile != null) {
                        val dosPath = exeFile.relativeTo(gameDir).path.replace("/", "\\\\")
                        "wine \"A:\\\\${dosPath}\""
                    } else {
                        "wine \"A:\\\\\""
                    }
                }

                var containers = containerManager.getContainers()
                var container = containers.firstOrNull()

                if (container == null) {
                    try {
                        val data = org.json.JSONObject()
                        data.put("name", "Default")
                        data.put("wineVersion", com.winlator.cmod.core.WineInfo.MAIN_WINE_VERSION.identifier())
                        val contentsManager = com.winlator.cmod.contents.ContentsManager(context)
                        contentsManager.syncContents()
                        container = containerManager.createContainer(data, contentsManager)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (container == null) {
                    android.widget.Toast.makeText(context, "Failed to build container", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }

                mountADrive(container, gameInstallPath)

                val desktopDir = container.getDesktopDir()
                if (!desktopDir.exists()) desktopDir.mkdirs()
                val shortcutFile = java.io.File(desktopDir, "${app.appName}.desktop")
                val content = java.lang.StringBuilder()
                content.append("[Desktop Entry]\n")
                content.append("Type=Application\n")
                content.append("Name=${app.title}\n")
                content.append("Exec=$execCmd\n")
                content.append("Icon=epic_icon_${app.id}\n")
                content.append("\n[Extra Data]\n")
                content.append("game_source=EPIC\n")
                content.append("app_id=${app.id}\n")
                content.append("container_id=${container.id}\n")
                content.append("game_install_path=${gameInstallPath}\n")

                com.winlator.cmod.core.FileUtils.writeString(shortcutFile, content.toString())

                container.saveData()

                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", container.id)
                intent.putExtra("shortcut_path", shortcutFile.path)
                intent.putExtra("shortcut_name", app.title)
                intent.putExtra("extra_exec_args", args) // Pass fresh tokens
                context.startActivity(intent)
            }
        }
    }

    private fun launchGogGame(context: android.content.Context, containerManager: ContainerManager, app: GOGGame) {
        val gameInstallPath = app.installPath.takeIf { it.isNotEmpty() } ?: GOGConstants.getGameInstallPath(app.title)
        val gameDir = java.io.File(gameInstallPath)
        if (!gameDir.exists()) {
            android.widget.Toast.makeText(context, "Game not installed: ${app.title}", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        var existingShortcut = containerManager.loadShortcuts().find {
            it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == app.id
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val gogAppId = "GOG_${app.id}"
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                GOGService.syncCloudSaves(context, gogAppId)
            }

            if (existingShortcut != null) {
                val shortcut = existingShortcut!!
                shortcut.putExtra("game_install_path", gameInstallPath)

                // Repair broken Exec line if exe path is missing (just "A:\")
                val currentPath = shortcut.path
                if (currentPath == null || currentPath == "A:\\" || currentPath == "A:\\\\") {
                    val newExecCmd = if (shortcut.getExtra("launch_exe_path").isNotEmpty()) {
                        val selectedExe = java.io.File(shortcut.getExtra("launch_exe_path"))
                        if (selectedExe.exists()) {
                            val normalizedBaseDir = java.io.File(gameInstallPath).absolutePath.removeSuffix("/")
                            val normalizedExePath = selectedExe.absolutePath
                            if (normalizedExePath == normalizedBaseDir || normalizedExePath.startsWith("$normalizedBaseDir/")) {
                                val dosPath = selectedExe.relativeTo(java.io.File(gameInstallPath)).path.replace("/", "\\\\")
                                "wine \"A:\\\\${dosPath}\""
                            } else {
                                val hostPath = normalizedExePath.replace("/", "\\\\").let { if (it.startsWith("\\")) it else "\\$it" }
                                "wine \"Z:${hostPath}\""
                            }
                        } else null
                    } else {
                        val libraryItem = LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.steam.enums.GameSource.GOG)
                        val exePath = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            GOGService.getInstalledExe(libraryItem)
                        }
                        if (exePath.isNotEmpty()) {
                            "wine \"A:\\\\${exePath.replace("/", "\\\\")}\""
                        } else {
                            val exeFile = findGameExe(gameDir)
                            if (exeFile != null) {
                                val dosPath = exeFile.relativeTo(gameDir).path.replace("/", "\\\\")
                                "wine \"A:\\\\${dosPath}\""
                            } else null
                        }
                    }
                    if (newExecCmd != null) {
                        val lines = com.winlator.cmod.core.FileUtils.readLines(shortcut.file)
                        val sb = StringBuilder()
                        for (line in lines) {
                            if (line.startsWith("Exec=")) {
                                sb.append("Exec=$newExecCmd\n")
                            } else {
                                sb.append(line).append("\n")
                            }
                        }
                        com.winlator.cmod.core.FileUtils.writeString(shortcut.file, sb.toString())
                    }
                }

                shortcut.saveData()
                mountADrive(shortcut.container, gameInstallPath)

                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", shortcut.container.id)
                intent.putExtra("shortcut_path", shortcut.file.path)
                intent.putExtra("shortcut_name", shortcut.name)
                context.startActivity(intent)
                return@launch
            }

            val libraryItem = LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.steam.enums.GameSource.GOG)
            val exePath = withContext(kotlinx.coroutines.Dispatchers.IO) {
                GOGService.getInstalledExe(libraryItem)
            }
            val execCmd = if (exePath.isNotEmpty()) {
                "wine \"A:\\\\${exePath.replace("/", "\\\\")}\""
            } else {
                val exeFile = findGameExe(gameDir)
                if (exeFile != null) {
                    val dosPath = exeFile.relativeTo(gameDir).path.replace("/", "\\\\")
                    "wine \"A:\\\\${dosPath}\""
                } else {
                    "wine \"A:\\\\\""
                }
            }

            var container = containerManager.getContainers().firstOrNull()
            if (container == null) {
                try {
                    val data = org.json.JSONObject()
                    data.put("name", "Default")
                    data.put("wineVersion", com.winlator.cmod.core.WineInfo.MAIN_WINE_VERSION.identifier())
                    val contentsManager = com.winlator.cmod.contents.ContentsManager(context)
                    contentsManager.syncContents()
                    container = containerManager.createContainer(data, contentsManager)
                } catch (_: Exception) {}
            }

            if (container == null) {
                android.widget.Toast.makeText(context, "Failed to build container", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            mountADrive(container, gameInstallPath)

            val desktopDir = container.getDesktopDir()
            if (!desktopDir.exists()) desktopDir.mkdirs()
            val shortcutFile = java.io.File(desktopDir, "${app.title.replace("/", "_")}.desktop")
            val content = java.lang.StringBuilder()
            content.append("[Desktop Entry]\n")
            content.append("Type=Application\n")
            content.append("Name=${app.title}\n")
            content.append("Exec=$execCmd\n")
            content.append("Icon=gog_icon_${app.id}\n")
            content.append("\n[Extra Data]\n")
            content.append("game_source=GOG\n")
            content.append("gog_id=${app.id}\n")
            content.append("app_id=${gogPseudoId(app.id)}\n")
            content.append("container_id=${container.id}\n")
            content.append("game_install_path=${gameInstallPath}\n")

            com.winlator.cmod.core.FileUtils.writeString(shortcutFile, content.toString())
            container.saveData()

            val intent = Intent(context, XServerDisplayActivity::class.java)
            intent.putExtra("container_id", container.id)
            intent.putExtra("shortcut_path", shortcutFile.path)
            intent.putExtra("shortcut_name", app.title)
            context.startActivity(intent)
        }
    }

    private fun mountADrive(container: com.winlator.cmod.container.Container, gamePath: String) {
        val currentDrives = container.drives ?: com.winlator.cmod.container.Container.DEFAULT_DRIVES
        val sb = StringBuilder()
        for (drive in com.winlator.cmod.container.Container.drivesIterator(currentDrives)) {
            if (drive[0] != "A") {
                sb.append(drive[0]).append(':').append(drive[1])
            }
        }
        sb.append("A:").append(gamePath)
        container.drives = sb.toString()
    }

    // Launch custom game by shortcut name
    private fun launchCustomGame(context: android.content.Context, containerManager: ContainerManager, gameName: String) {
        val allShortcuts = containerManager.loadShortcuts()
        val customShortcuts = allShortcuts.filter { it.getExtra("game_source") == "CUSTOM" }

        // Try matching by custom_name extra first, then fall back to shortcut.name (filename)
        var shortcut = customShortcuts.find { it.getExtra("custom_name") == gameName }
            ?: customShortcuts.find { it.name == gameName }
            ?: customShortcuts.find { it.name == gameName.replace("/", "_").replace("\\", "_") }

        // If still not found, try matching by looking at the safe filename directly
        if (shortcut == null) {
            val safeName = gameName.replace("/", "_").replace("\\", "_")
            for (container in containerManager.containers) {
                val desktopFile = java.io.File(container.getDesktopDir(), "$safeName.desktop")
                if (desktopFile.exists()) {
                    shortcut = com.winlator.cmod.container.Shortcut(container, desktopFile)
                    break
                }
            }
        }

        if (shortcut == null) {
            android.widget.Toast.makeText(context, "Custom game shortcut not found: $gameName", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Backfill custom_name if missing (legacy shortcuts)
        if (shortcut.getExtra("custom_name").isEmpty()) {
            shortcut.putExtra("custom_name", gameName)
            shortcut.saveData()
        }

        // Ensure A: drive is mounted to the game folder
        val gameFolder = shortcut.getExtra("custom_game_folder", "")
        if (gameFolder.isNotEmpty()) {
            mountADrive(shortcut.container, gameFolder)
            shortcut.container.saveData()
        }
        val intent = Intent(context, XServerDisplayActivity::class.java)
        intent.putExtra("container_id", shortcut.container.id)
        intent.putExtra("shortcut_path", shortcut.file.path)
        intent.putExtra("shortcut_name", gameName)
        context.startActivity(intent)
    }

    private fun findGameExe(dir: java.io.File): java.io.File? {
        // BFS: check each directory level fully before going deeper
        val exclusions = listOf("unins", "redist", "setup", "dotnet", "vcredist", 
            "dxsetup", "helper", "crash", "ue4prereq", "dxwebsetup", "launcher")
        
        var currentDirs = listOf(dir)
        var depth = 0
        var fallbackExe: java.io.File? = null
        
        while (currentDirs.isNotEmpty() && depth <= 4) {
            val nextDirs = mutableListOf<java.io.File>()
            val candidates = mutableListOf<java.io.File>()
            
            for (d in currentDirs) {
                val children = d.listFiles() ?: continue
                for (f in children) {
                    if (f.isDirectory) {
                        nextDirs.add(f)
                    } else if (f.extension.equals("exe", ignoreCase = true)) {
                        val name = f.name.lowercase()
                        if (exclusions.none { name.contains(it) }) {
                            candidates.add(f)
                        }
                    }
                }
            }
            
            // Prefer 64-bit executable candidates at the current depth
            val exe64 = candidates.find { 
                it.name.lowercase().contains("64") || 
                it.parentFile?.name?.lowercase()?.contains("64") == true
            }
            if (exe64 != null) return exe64
            
            // Collect the first valid candidate as a fallback
            if (fallbackExe == null && candidates.isNotEmpty()) {
                fallbackExe = candidates.first()
            }
            
            currentDirs = nextDirs
            depth++
        }
        return fallbackExe
    }


    @Composable
    fun EmptyStateMessage(message: String) {
        Text(message, color = TextSecondary, modifier = Modifier.padding(16.dp))
    }

    @Composable
    fun LoginRequiredScreen(storeName: String, onLoginClick: () -> Unit) {
        val message = if (storeName == "Library") "Sign into a store or add a custom game to build your library." else "Please sign in to access the $storeName store."
        val buttonText = if (storeName == "Library") "Manage Store Accounts" else "Sign into $storeName"
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(message, color = TextPrimary, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onLoginClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp).fillMaxWidth(0.7f)
                ) { Text(buttonText, fontWeight = FontWeight.Bold) }
            }
        }
    }

    // Drawer content: avatar card + filters
    @Composable
    private fun DrawerContent(
        persona: com.winlator.cmod.steam.data.SteamFriend?,
        context: android.content.Context,
        scope: kotlinx.coroutines.CoroutineScope,
        aioMode: Boolean,
        onAioToggle: (Boolean) -> Unit,
        storeVisible: SnapshotStateMap<String, Boolean>,
        contentFilters: SnapshotStateMap<String, Boolean>,
        libraryLayoutMode: LibraryLayoutMode,
        onLibraryLayoutSelected: (LibraryLayoutMode) -> Unit,
        onClose: () -> Unit,
    ) {
        val currentState = persona?.state ?: EPersonaState.Online
        var statusExpanded by remember { mutableStateOf(false) }

        ModalDrawerSheet(
            drawerContainerColor = BgDark,
            drawerContentColor = TextPrimary,
            modifier = Modifier.width(300.dp)
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // ── Avatar Card ──
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { statusExpanded = !statusExpanded }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val avatarUrl = persona?.avatarHash?.getAvatarURL()
                                ?: "https://steamcdn-a.akamaihd.net/steamcommunity/public/images/avatars/fe/fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb_full.jpg"

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true).build(),
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = persona?.name ?: "Not signed in",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val statusLabel = when (currentState) {
                                    EPersonaState.Online -> "Online"
                                    EPersonaState.Away -> "Away"
                                    else -> "Offline"
                                }
                                val statusColor = when (currentState) {
                                    EPersonaState.Online -> StatusOnline
                                    EPersonaState.Away -> StatusAway
                                    else -> StatusOffline
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    Text(statusLabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }

                            val chevronRotation by animateFloatAsState(
                                targetValue = if (statusExpanded) 90f else 0f,
                                animationSpec = tween(250),
                                label = "chevronRotation"
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Toggle status",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = chevronRotation }
                            )
                        }

                        // Expandable status options
                        AnimatedVisibility(visible = statusExpanded) {
                            Column(Modifier.padding(top = 12.dp)) {
                                HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                                Spacer(Modifier.height(8.dp))
                                Text("STATUS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(Modifier.height(8.dp))

                                listOf(
                                    Triple(EPersonaState.Online, "Online", StatusOnline),
                                    Triple(EPersonaState.Away, "Away", StatusAway),
                                    Triple(EPersonaState.Invisible, "Invisible", StatusOffline)
                                ).forEach { (state, label, color) ->
                                    val isSelected = currentState == state
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Accent.copy(alpha = 0.1f) else Color.Transparent)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                scope.launch { SteamService.setPersonaState(state) }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(Modifier.size(10.dp).background(color, CircleShape))
                                        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.weight(1f))
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))
                Spacer(Modifier.height(20.dp))

                // ── Layouts ──
                Text("LAYOUTS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton(
                        label = "4-Grid",
                        checked = libraryLayoutMode == LibraryLayoutMode.GRID_4,
                        modifier = Modifier.weight(1f)
                    ) { if (it) onLibraryLayoutSelected(LibraryLayoutMode.GRID_4) }
                    DrawerFilterButton(
                        label = "Carousel",
                        checked = libraryLayoutMode == LibraryLayoutMode.CAROUSEL,
                        modifier = Modifier.weight(1f)
                    ) { if (it) onLibraryLayoutSelected(LibraryLayoutMode.CAROUSEL) }
                }

                Spacer(Modifier.height(16.dp))

                // ── Stores ──
                Text("STORES", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.height(8.dp))

                DrawerFilterButton("AIO Store Mode", aioMode, Modifier.fillMaxWidth()) { onAioToggle(it) }
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton("Steam", storeVisible["steam"] == true, Modifier.weight(1f)) { storeVisible["steam"] = it }
                    DrawerFilterButton("Epic", storeVisible["epic"] == true, Modifier.weight(1f)) { storeVisible["epic"] = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton("GOG", storeVisible["gog"] == true, Modifier.weight(1f)) { storeVisible["gog"] = it }
                    DrawerFilterButton("Amazon", storeVisible["amazon"] == true, Modifier.weight(1f)) { storeVisible["amazon"] = it }
                }

                Spacer(Modifier.height(16.dp))

                // ── Content Types ──
                Text("CONTENT TYPES", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton("Games", contentFilters["games"] == true, Modifier.weight(1f)) { contentFilters["games"] = it }
                    DrawerFilterButton("DLC", contentFilters["dlc"] == true, Modifier.weight(1f)) { contentFilters["dlc"] = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton("Applications", contentFilters["applications"] == true, Modifier.weight(1f)) { contentFilters["applications"] = it }
                    DrawerFilterButton("Tools", contentFilters["tools"] == true, Modifier.weight(1f)) { contentFilters["tools"] = it }
                }
            }
        }
    }

    @Composable
    private fun DrawerFilterButton(label: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
        val bgColor = if (checked) Accent.copy(alpha = 0.2f) else CardDark
        val borderColor = if (checked) Accent else Color.Transparent
        val textColor = if (checked) Accent else TextSecondary

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggle(!checked) }
                .padding(vertical = 10.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = textColor, fontWeight = FontWeight.Bold)
        }
    }

    // Smart game folder detection
    private fun detectGameFolder(exePath: String): String {
        val exeFile = java.io.File(exePath)
        // Directories that are typically sub-folders inside a game, not the root
        val subDirNames = setOf(
            "bin", "binaries", "x64", "x86", "win64", "win32",
            "bin64", "bin32", "game", "build", "release",
            "shipping", "debug", "retail", "dist"
        )
        var dir = exeFile.parentFile ?: return exePath
        // Walk up while the current dir name looks like a sub-directory
        while (dir.parentFile != null) {
            val name = dir.name.lowercase()
            if (name in subDirNames) {
                dir = dir.parentFile!!
            } else {
                break
            }
        }
        return dir.absolutePath
    }

    // Add Custom Game Dialog
    @Composable
    private fun AddCustomGameDialog(onDismiss: () -> Unit) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var selectedExePath by remember { mutableStateOf<String?>(null) }
        var gameName by remember { mutableStateOf("") }
        var gameFolder by remember { mutableStateOf<String?>(null) }
        var isAdding by remember { mutableStateOf(false) }

        val exePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                // Resolve to a real file path
                val path = getPathFromContentUri(context, uri)
                if (path != null && path.lowercase().endsWith(".exe")) {
                    selectedExePath = path
                    gameFolder = detectGameFolder(path)
                    // Auto-generate a game name from the folder name
                    if (gameName.isBlank()) {
                        gameName = java.io.File(gameFolder!!).name
                            .replace("_", " ").replace("-", " ")
                    }
                } else {
                    android.widget.Toast.makeText(context, "Please select a .exe file", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri -> uri?.let { gameFolder = getPathFromTreeUri(it) } }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .heightIn(max = 320.dp),
                shape = RoundedCornerShape(16.dp),
                color = CardDark,
                shadowElevation = 16.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    // Header row with title + cancel/add buttons all in one line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Custom Game", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss, modifier = Modifier.height(34.dp)) {
                            Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = {
                                if (selectedExePath == null || gameName.isBlank() || gameFolder == null) {
                                    android.widget.Toast.makeText(context, "Select an exe and provide a name", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isAdding = true
                                scope.launch(Dispatchers.IO) {
                                    addCustomGame(context, gameName.trim(), selectedExePath!!, gameFolder!!)
                                    withContext(Dispatchers.Main) {
                                        isAdding = false
                                        android.widget.Toast.makeText(context, "$gameName added!", android.widget.Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }
                            },
                            enabled = selectedExePath != null && gameName.isNotBlank() && gameFolder != null && !isAdding,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            if (isAdding) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Scrollable content area
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Pick EXE button
                        Button(
                            onClick = { exePickerLauncher.launch(arrayOf("application/octet-stream", "application/x-msdos-program", "application/x-msdownload", "*/*")) },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (selectedExePath == null) "Select Executable (.exe)" else java.io.File(selectedExePath!!).name,
                                color = if (selectedExePath == null) TextSecondary else TextPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp
                            )
                        }

                        if (selectedExePath != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(selectedExePath!!, color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

                            Spacer(Modifier.height(10.dp))

                            // Game name text field — compact
                            OutlinedTextField(
                                value = gameName,
                                onValueChange = { gameName = it },
                                label = { Text("Game Name", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Accent,
                                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = Accent,
                                    focusedLabelColor = Accent,
                                    unfocusedLabelColor = TextSecondary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(Modifier.height(10.dp))

                            // Game folder — single compact row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceDark)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = StatusOnline.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Game Folder (A: drive)", color = TextSecondary, fontSize = 10.sp)
                                    Text(
                                        gameFolder ?: "Auto-detected",
                                        color = if (gameFolder != null) TextPrimary else TextSecondary,
                                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { folderPickerLauncher.launch(null) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Change", tint = Accent, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Resolve content URI to real file path
    private fun getPathFromContentUri(context: android.content.Context, uri: Uri): String? {
        // Try DocumentsContract first
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                if (docId.startsWith("primary:")) {
                    return "${android.os.Environment.getExternalStorageDirectory().path}/${docId.substringAfter(":")}"
                } else if (docId.contains(":")) {
                    val parts = docId.split(":", limit = 2)
                    return if (parts.size == 2) "/storage/${parts[0]}/${parts[1]}" else null
                }
            }
        } catch (_: Exception) {}
        // Fallback: uri.path
        return uri.path
    }

    // Create custom game shortcut + container
    private fun addCustomGame(context: android.content.Context, name: String, exePath: String, gameFolderPath: String) {
        val containerManager = ContainerManager(context)
        var containers = containerManager.getContainers()
        var container = containers.firstOrNull()
        if (container == null) {
            try {
                val data = org.json.JSONObject()
                data.put("name", "Default")
                data.put("wineVersion", "proton-9.0-x86_64")
                val contentsManager = com.winlator.cmod.contents.ContentsManager(context)
                contentsManager.syncContents()
                container = containerManager.createContainer(data, contentsManager)
            } catch (e: Exception) { e.printStackTrace() }
        }
        if (container == null) return

        // Mount the game folder as A: drive
        mountADrive(container, gameFolderPath)

        // Build the relative exe path from gameFolder
        val exeFile = java.io.File(exePath)
        val gameFolderFile = java.io.File(gameFolderPath)
        val dosPath = try {
            exeFile.relativeTo(gameFolderFile).path.replace("/", "\\\\")
        } catch (_: Exception) {
            exeFile.name
        }
        val execCmd = "wine \"A:\\\\$dosPath\""

        // Write .desktop shortcut
        val desktopDir = container.getDesktopDir()
        if (!desktopDir.exists()) desktopDir.mkdirs()
        val safeName = name.replace("/", "_").replace("\\", "_")
        val shortcutFile = java.io.File(desktopDir, "$safeName.desktop")
        val content = StringBuilder()
        content.append("[Desktop Entry]\n")
        content.append("Type=Application\n")
        content.append("Name=$name\n")
        content.append("Exec=$execCmd\n")
        content.append("Icon=custom_game\n")
        content.append("\n[Extra Data]\n")
        content.append("game_source=CUSTOM\n")
        content.append("custom_name=$name\n")
        content.append("custom_exe=$exePath\n")
        content.append("custom_game_folder=$gameFolderPath\n")
        content.append("container_id=${container.id}\n")
        com.winlator.cmod.core.FileUtils.writeString(shortcutFile, content.toString())
        container.saveData()

        // Extract exe icon and save as PNG for carousel artwork
        try {
            val iconOutFile = java.io.File(context.filesDir, "custom_icons/$safeName.png")
            PeIconExtractor.extractAndSave(java.io.File(exePath), iconOutFile)
        } catch (_: Exception) {}
    }

    // Cloud Sync UI Overlay
    @Composable
    fun CloudSyncOverlay(status: SteamService.Companion.CloudSyncMessage) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(enabled=false, onClick={}),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardDark,
                modifier = Modifier.width(340.dp).padding(16.dp).border(2.dp, Accent.copy(alpha=0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val title = if (status.isUpload) "Cloud Sync Uploading..." else "Cloud Sync Downloading..."
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))

                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { status.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Accent,
                        trackColor = SurfaceDark
                    )

                    Spacer(Modifier.height(8.dp))
                    val pct = (status.progress * 100).toInt()
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }
        }
    }

    @Composable
    fun CustomPathWarningDialog(onDismiss: () -> Unit, onProceed: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardDark,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Custom Download Path",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "A default download path has already been configured in Settings > Stores. If you wish to set a unique custom path for this specific installation, click Proceed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Close", color = TextSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onProceed,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Proceed")
                        }
                    }

                }
            }
        }
    }
}

@Composable
fun ControllerBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF30363D), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF8B949E).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFFE6EDF3),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
