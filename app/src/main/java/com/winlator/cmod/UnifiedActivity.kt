package com.winlator.cmod

import android.content.Intent
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
import androidx.compose.foundation.border
import com.winlator.cmod.widget.chasingBorder
import androidx.compose.foundation.clickable
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
private val StatusOnline = Color(0xFF3FB950)
private val StatusAway = Color(0xFFF0C040)
private val StatusOffline = Color(0xFF6E7681)


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
    private var lastLibraryMoveTime = 0L
    private val libraryColumns = 4

    private fun moveLibraryFocus(left: Boolean, right: Boolean, up: Boolean, down: Boolean) {
        val idx = libraryFocusIndex.value
        val count = libraryItemCount
        if (count <= 0) return
        var newIdx = idx
        if (left) newIdx = (idx - 1).coerceAtLeast(0)
        if (right) newIdx = (idx + 1).coerceAtMost(count - 1)
        if (up) newIdx = (idx - libraryColumns).coerceAtLeast(0)
        if (down) newIdx = (idx + libraryColumns).coerceAtMost(count - 1)
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
        if (GOGService.hasStoredCredentials(this) && !GOGService.isRunning) {
            GOGService.start(this)
        }
        libraryRefreshSignal++
    }

    override fun onStop() {
        super.onStop()
        if (GOGService.isRunning && !isChangingConfigurations && !GOGService.hasActiveOperations()) {
            GOGService.stop()
        }
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
        
        val currentRefreshSignal = this@UnifiedActivity.libraryRefreshSignal
        LaunchedEffect(currentRefreshSignal) {
            libraryRefreshKey++
        }
        
        val contentFilters = remember { mutableStateMapOf("games" to true, "dlc" to false, "applications" to false, "tools" to false) }
        val tabs = remember(aioMode, storeVisible.toMap()) { buildTabs(aioMode, storeVisible) }
        var selectedIdx by rememberSaveable { mutableIntStateOf(0) }
        var selectedDownloadId by remember { mutableStateOf<String?>(null) }
        var showFilter by remember { mutableStateOf(false) }
        val isLoggedIn by SteamService.isLoggedInFlow.collectAsState()
        val isEpicLoggedIn by EpicAuthManager.isLoggedInFlow.collectAsState()
        val isGogLoggedIn by GOGAuthManager.isLoggedInFlow.collectAsState()
        val steamApps by db.steamAppDao().getAllOwnedApps().collectAsState(initial = emptyList())
        val context = LocalContext.current
        val persona by SteamService.instance?.localPersona?.collectAsState()
            ?: remember { mutableStateOf(null) }
        val scope = rememberCoroutineScope()
        
        // Use libraryRefreshKey as a key for remember so we re-collect from DB when it changes
        val epicApps by remember(libraryRefreshKey) { 
            db.epicGameDao().getAll() 
        }.collectAsState(initial = emptyList())
        val gogApps by remember(libraryRefreshKey) {
            db.gogGameDao().getAll()
        }.collectAsState(initial = emptyList())

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

        LaunchedEffect(Unit) {
            while(true) {
                kotlinx.coroutines.delay(10000)
                SteamService.syncStates()
                libraryRefreshKey++
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
                            showFilter = !showFilter
                        }
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_B -> {
                        // Close menus in order, or show exit confirmation if none are open
                        if (showFilter) showFilter = false
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
                            val anyActive = DownloadService.getAllDownloads().any { it.second.isActive() }
                            if (anyActive) SteamService.pauseAll() else SteamService.resumeAll()
                        }
                    }
                    android.view.KeyEvent.KEYCODE_BUTTON_R2 -> {
                        if (key == "downloads") {
                            SteamService.cancelAll()
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().background(BgDark)) {
            Scaffold(
                containerColor = BgDark,
                topBar = { TopBar(tabs, selectedIdx, { selectedIdx = it }, persona, context, scope, isControllerConnected, isPS, isLibraryTab, searchQuery, { searchQuery = it }) {
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
                            "library" -> LibraryCarousel(isLoggedIn, filteredSteamApps, epicApps, gogApps, libraryRefreshKey, searchQuery)
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

                    // Bottom-left filter button
                    if (key != "downloads") {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .shadow(8.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                                    .clip(CircleShape)
                                    .background(SurfaceDark)
                                    .focusProperties { canFocus = !isLibraryTab }
                                    .clickable { showFilter = !showFilter },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = TextPrimary, modifier = Modifier.size(24.dp))
                            }
                            if (isControllerConnected) {
                                Spacer(Modifier.width(12.dp))
                                ControllerBadge(if (isPS) "Square" else "X")
                            }
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

            if (showFilter) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { showFilter = false }
                        )
                )
            }

            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.BottomStart) {
                FilterPanel(
                    visible = showFilter,
                    onDismiss = { showFilter = false },
                    aioMode = aioMode,
                    onAioToggle = { aioMode = it; PrefManager.aioStoreMode = it },
                    storeVisible = storeVisible,
                    contentFilters = contentFilters
                )
            }
        }

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
            if (showFilter) {
                showFilter = false
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
        onGameSettingsClicked: () -> Unit
    ) {
        var showStatusMenu by remember { mutableStateOf(false) }
        val currentState = persona?.state ?: EPersonaState.Online
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

                Box(modifier = Modifier.focusProperties { canFocus = !isLibraryTab }) {
                    val avatarUrl = persona?.avatarHash?.getAvatarURL()
                        ?: "https://steamcdn-a.akamaihd.net/steamcommunity/public/images/avatars/fe/fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb_full.jpg"

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                            .clip(CircleShape)
                            .clickable { showStatusMenu = true }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true).build(),
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        val statusColor = when (currentState) {
                            EPersonaState.Online -> StatusOnline
                            EPersonaState.Away -> StatusAway
                            else -> StatusOffline
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .offset((-1).dp, (-1).dp)
                                .background(BgDark, CircleShape)
                                .padding(2.dp)
                                .background(statusColor, CircleShape)
                        )
                    }

                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false },
                        modifier = Modifier
                            .width(200.dp)
                            .background(SurfaceDark, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            "STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        listOf(
                            Triple(EPersonaState.Online, "Online", StatusOnline),
                            Triple(EPersonaState.Away, "Away", StatusAway),
                            Triple(EPersonaState.Invisible, "Invisible", StatusOffline)
                        ).forEach { (state, label, color) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(Modifier.size(10.dp).background(color, CircleShape))
                                        Text(label, color = TextPrimary)
                                        Spacer(Modifier.weight(1f))
                                        if (currentState == state) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    showStatusMenu = false
                                    scope.launch { SteamService.setPersonaState(state) }
                                }
                            )
                        }
                    }
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
        libraryRefreshKey: Int = 0,
        searchQuery: String = "",
    ) {
        val context = LocalContext.current

        // Load custom game shortcuts from containers
        var customApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        LaunchedEffect(libraryRefreshKey) {
            withContext(Dispatchers.IO) {
                try {
                    val cm = ContainerManager(context)
                    val apps = cm.loadShortcuts()
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
                    withContext(Dispatchers.Main) { customApps = apps }
                } catch (_: Exception) {}
            }
        }

        val steamInstalled = remember(steamApps, libraryRefreshKey) {
            steamApps.filter { SteamService.isAppInstalled(it.id) }
        }

        val epicInstalled = remember(epicApps, libraryRefreshKey) {
            epicApps.filter { it.isInstalled }
        }

        val gogInstalled = remember(gogApps, libraryRefreshKey) {
            gogApps.filter { it.isInstalled && java.io.File(it.installPath).exists() }
        }

        val gogByPseudoId = remember(gogInstalled) {
            gogInstalled.associateBy { gogPseudoId(it.id) }
        }

        val installedApps = remember(steamInstalled, customApps, epicInstalled, gogInstalled, libraryRefreshKey) {
            val playtimePrefs = context.getSharedPreferences("playtime_stats", android.content.Context.MODE_PRIVATE)
            // Map Epic games to pseudo SteamApp objects with large ID offset
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
            merged.sortedByDescending { app ->
                val searchKey = if (app.id >= 2000000000) {
                    app.name
                } else if (app.id < 0) {
                    app.name
                } else {
                    app.name.replace("[^A-Za-z0-9 _-]".toRegex(), "")
                }
                playtimePrefs.getLong("${searchKey}_last_played", 0L)
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
        val activity = LocalContext.current as? UnifiedActivity

        // Keep activity's item count in sync
        LaunchedEffect(displayedApps.size) {
            activity?.libraryItemCount = displayedApps.size
        }

        // FocusRequesters for each grid item
        val focusRequesters = remember(displayedApps.size) {
            List(displayedApps.size) { FocusRequester() }
        }

        // Observe focus index changes from the activity and request focus on the target item
        val focusIndex by (activity?.libraryFocusIndex ?: kotlinx.coroutines.flow.MutableStateFlow(0)).collectAsState()
        LaunchedEffect(focusIndex, focusRequesters.size) {
            if (focusRequesters.isNotEmpty() && focusIndex in focusRequesters.indices) {
                // Scroll to make the focused item visible
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

        FourByTwoGridView(
            items = displayedApps,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            gridState = gridState,
            contentPadding = PaddingValues(vertical = 16.dp), // 6dp border inset + 10dp
            clipContent = false,
        ) { app, index, rowHeight ->
            GameCapsule(
                app = app,
                gogGame = gogByPseudoId[app.id],
                isFocusedOverride = index == focusIndex,
                modifier = Modifier
                    .height(rowHeight)
                    .then(
                        if (index in focusRequesters.indices)
                            Modifier.focusRequester(focusRequesters[index])
                        else Modifier
                    )
            )
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

    // Game Settings Dialog
    @Composable
    private fun GameSettingsDialog(app: SteamApp, onDismissRequest: () -> Unit) {
        val context = LocalContext.current
        var currentTab by remember { mutableStateOf("Menu") }
        val scope = rememberCoroutineScope()
        val isCustom = app.id < 0
        val isEpic = app.id >= 2000000000
        val epicId = if (isEpic) app.id - 2000000000 else 0
        
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

        Dialog(onDismissRequest = onDismissRequest) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = CardDark
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(app.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    when (currentTab) {
                        "Menu" -> {
                            Button(
                                onClick = {
                                    if (isCustom || isEpic) {
                                        val cm = ContainerManager(context)
                                        val sc = cm.loadShortcuts().find {
                                            if (isCustom) {
                                                it.getExtra("game_source") == "CUSTOM" && (it.getExtra("custom_name") == app.name || it.name == app.name)
                                            } else {
                                                it.getExtra("game_source") == "EPIC" && it.getExtra("app_id") == epicId.toString()
                                            }
                                        }
                                        if (sc != null) {
                                            val intent = Intent(context, MainActivity::class.java)
                                            intent.putExtra("edit_shortcut_path", sc.file.absolutePath)
                                            intent.putExtra("return_to_unified", true)
                                            val opts = ActivityOptionsCompat.makeCustomAnimation(context, R.anim.settings_enter, R.anim.settings_exit)
                                            context.startActivity(intent, opts.toBundle())
                                        } else if (isEpic) {
                                            val intent = Intent(context, MainActivity::class.java)
                                            intent.putExtra("create_shortcut_for_epic_id", epicId)
                                            intent.putExtra("create_shortcut_for_app_name", app.name)
                                            intent.putExtra("return_to_unified", true)
                                            val opts = ActivityOptionsCompat.makeCustomAnimation(context, R.anim.settings_enter, R.anim.settings_exit)
                                            context.startActivity(intent, opts.toBundle())
                                        }
                                    } else {
                                        val intent = Intent(context, MainActivity::class.java)
                                        intent.putExtra("create_shortcut_for_app_id", app.id)
                                        intent.putExtra("create_shortcut_for_app_name", app.name)
                                        intent.putExtra("return_to_unified", true)
                                        val opts = ActivityOptionsCompat.makeCustomAnimation(context, R.anim.settings_enter, R.anim.settings_exit)
                                        context.startActivity(intent, opts.toBundle())
                                    }
                                    onDismissRequest()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Settings") }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    currentTab = "Saves"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Saves") }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { currentTab = "Uninstall" },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(if (isCustom) "Remove Game" else "Uninstall Game") }
                        }
                        "Saves" -> {
                            Text("Import or export your game saves for this game. For best results, ensure the game is closed.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { exportLauncher.launch("${app.name.replace(" ", "_").replace(":", "")}_Saves.zip") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Export Saves to ZIP") }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { importLauncher.launch(arrayOf("application/zip")) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Import Saves from ZIP") }
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { currentTab = "Menu" }, modifier = Modifier.align(Alignment.End)) {
                                Text("Back", color = TextSecondary)
                            }
                        }
                        "Uninstall" -> {
                            Text(
                                if (isCustom) "Remove ${app.name} from your library? The game files on disk will not be deleted."
                                else "Are you sure you want to uninstall ${app.name}? This will permanently delete the game folder.",
                                color = Color(0xFFFF6B6B)
                            )
                            Spacer(Modifier.height(16.dp))
                            var isUninstalling by remember { mutableStateOf(false) }
                            if (isUninstalling) {
                                CircularProgressIndicator(color = Color(0xFFFF4444), modifier = Modifier.align(Alignment.CenterHorizontally))
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { currentTab = "Menu" }) { Text("Cancel", color = TextSecondary) }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            isUninstalling = true
                                            if (isCustom) {
                                                // Remove custom game shortcut + icon
                                                scope.launch(Dispatchers.IO) {
                                                    val cm = ContainerManager(context)
                                                    val sc = cm.loadShortcuts().find {
                                                        it.getExtra("game_source") == "CUSTOM" && (it.getExtra("custom_name") == app.name || it.name == app.name)
                                                    }
                                                    sc?.file?.delete()
                                                    // Remove saved icon
                                                    val iconFile = java.io.File(context.filesDir, "custom_icons/${app.name.replace("/", "_")}.png")
                                                    iconFile.delete()
                                                    withContext(Dispatchers.Main) {
                                                        android.widget.Toast.makeText(context, "${app.name} removed.", android.widget.Toast.LENGTH_SHORT).show()
                                                        onDismissRequest()
                                                    }
                                                }
                                            } else if (isEpic) {
                                                scope.launch(Dispatchers.IO) {
                                                    val result = EpicService.deleteGame(context, epicId)
                                                    withContext(Dispatchers.Main) {
                                                        if (result.isSuccess) {
                                                            android.widget.Toast.makeText(context, "${app.name} uninstalled.", android.widget.Toast.LENGTH_SHORT).show()
                                                            // Trigger a local refresh of the list if needed, although the Flow should handle it
                                                        } else {
                                                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                                            android.widget.Toast.makeText(context, "Failed to uninstall: $error", android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                        onDismissRequest()
                                                    }
                                                }
                                            } else {
                                                SteamService.uninstallApp(app.id) { success ->
                                                    if (success) {
                                                        android.widget.Toast.makeText(context, "${app.name} uninstalled.", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Failed to uninstall.", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    onDismissRequest()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text(if (isCustom) "Confirm Remove" else "Confirm Uninstall") }
                                }
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
        var currentTab by remember { mutableStateOf("Menu") }
        val scope = rememberCoroutineScope()

        Dialog(onDismissRequest = onDismissRequest) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = CardDark
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(app.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    when (currentTab) {
                        "Menu" -> {
                            Button(
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
                                    val opts = ActivityOptionsCompat.makeCustomAnimation(context, R.anim.settings_enter, R.anim.settings_exit)
                                    context.startActivity(intent, opts.toBundle())
                                    onDismissRequest()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Settings") }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { currentTab = "Saves" },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Saves") }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { currentTab = "Uninstall" },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Uninstall Game") }
                        }
                        "Saves" -> {
                            Text(
                                "Sync this game's GOG cloud saves. For best results, ensure the game is closed.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        GOGService.syncCloudSaves(context, "GOG_${app.id}", "auto")
                                    }
                                    android.widget.Toast.makeText(context, "Cloud sync started.", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Sync Cloud Saves") }
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { currentTab = "Menu" }, modifier = Modifier.align(Alignment.End)) {
                                Text("Back", color = TextSecondary)
                            }
                        }
                        "Uninstall" -> {
                            Text(
                                "Are you sure you want to uninstall ${app.title}? This will permanently delete the game folder.",
                                color = Color(0xFFFF6B6B)
                            )
                            Spacer(Modifier.height(16.dp))
                            var isUninstalling by remember { mutableStateOf(false) }
                            if (isUninstalling) {
                                CircularProgressIndicator(color = Color(0xFFFF4444), modifier = Modifier.align(Alignment.CenterHorizontally))
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { currentTab = "Menu" }) { Text("Cancel", color = TextSecondary) }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            isUninstalling = true
                                            scope.launch(Dispatchers.IO) {
                                                GOGService.deleteGame(context, LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.steam.enums.GameSource.GOG))
                                                withContext(Dispatchers.Main) {
                                                    android.widget.Toast.makeText(context, "${app.title} uninstalled.", android.widget.Toast.LENGTH_SHORT).show()
                                                    onDismissRequest()
                                                }
                                            }
                                        },
                                        modifier = Modifier,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("Confirm Uninstall") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Single game capsule for carousel
    @Composable
    private fun GameCapsule(app: SteamApp, gogGame: GOGGame? = null, isFocusedOverride: Boolean = false, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val isCustom = app.id < 0
        val isEpic = app.id >= 2000000000
        val epicId = if (isEpic) app.id - 2000000000 else 0
        val epicGame by produceState<EpicGame?>(initialValue = null, key1 = epicId) {
            value = if (isEpic) db.epicGameDao().getById(epicId) else null
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
                .clickable {
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
                }
        ) {
            // Art area — clip only on the image, not the text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                val artModifier = Modifier.fillMaxSize()
                if (isCustom) {
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
                fontWeight = FontWeight.SemiBold,
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

        LaunchedEffect(Unit) {
            while (true) {
                val currentDownloads = DownloadService.getAllDownloads()
                downloads.clear()
                downloads.addAll(currentDownloads)
                if (selectedId != null && currentDownloads.none { it.first == selectedId }) {
                    onSelectDownload(null)
                }
                kotlinx.coroutines.delay(1000)
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val isController = ControllerHelper.isControllerConnected()
            val isPS = ControllerHelper.isPlayStationController()

            // Global Actions row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val selectedInfo = downloads.find { it.first == selectedId }?.second
                val isPaused = selectedInfo?.getStatusFlow()?.value == DownloadPhase.PAUSED
                
                val pauseResumeLabel = if (selectedId == null) {
                    val anyActive = downloads.any { it.second.isActive() }
                    if (anyActive) "Pause All" else "Resume All"
                } else {
                    if (isPaused) "Resume" else "Pause"
                }
                
                val cancelLabel = if (selectedId == null) "Cancel All" else "Cancel"

                // Download Queue Size
                var queueSize by remember { mutableIntStateOf(PrefManager.downloadQueueSize) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
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
                            val anyActive = downloads.any { it.second.isActive() }
                            if (anyActive) SteamService.pauseAll() else SteamService.resumeAll()
                        } else {
                            if (isPaused) {
                                val appId = selectedId.removePrefix("STEAM_").removePrefix("EPIC_").removePrefix("GOG_").toIntOrNull() ?: 0
                                if (selectedId.startsWith("STEAM_")) SteamService.downloadApp(appId)
                            } else {
                                selectedInfo?.cancel("Paused by user")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(if (isController) 0.35f else 0.25f),
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
                            SteamService.cancelAll()
                        } else {
                            selectedInfo?.cancel("Cancelled by user")
                            onSelectDownload(null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(if (isController) 0.5f else 0.33f),
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
                        DownloadPhase.PREPARING -> "Preparing..."
                        DownloadPhase.VERIFYING -> {
                            val filePart = currentFile?.let { " [${it.take(10)}]" } ?: ""
                            "Verifying...$filePart"
                        }
                        DownloadPhase.PATCHING -> "Patching..."
                        DownloadPhase.COMPLETE -> "Complete"
                        DownloadPhase.FAILED -> "Failed: ${if (statusMessage != null && statusMessage != "null") statusMessage else "Unknown error"}"
                        else -> status.name.lowercase().replaceFirstChar { it.uppercase() }
                    }
                    
                    Text("Status: $statusText", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
                            color = if (status == DownloadPhase.FAILED) Color(0xFFFF6B6B) else Accent,
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

                IconButton(onClick = { info.cancel() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFFF6B6B))
                }
                if (ControllerHelper.isControllerConnected()) {
                    Spacer(Modifier.width(8.dp))
                    ControllerBadge(if (ControllerHelper.isPlayStationController()) "Cross" else "A")
                }
            }
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

    // Filter panel
    @Composable
    private fun FilterPanel(
        visible: Boolean,
        onDismiss: () -> Unit,
        aioMode: Boolean,
        onAioToggle: (Boolean) -> Unit,
        storeVisible: SnapshotStateMap<String, Boolean>,
        contentFilters: SnapshotStateMap<String, Boolean>
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .padding(start = 16.dp, bottom = 72.dp)
                    .width(280.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                shadowElevation = 16.dp,
                tonalElevation = 4.dp
            ) {
                Column(Modifier
                    .padding(20.dp)
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FILTERS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))

                    // AIO Mode toggle
                    FilterButton("AIO Store Mode", aioMode, Modifier.fillMaxWidth()) { onAioToggle(it) }

                    Spacer(Modifier.height(16.dp))
                    Text("STORES", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterButton("Steam", storeVisible["steam"] == true, Modifier.weight(1f)) { storeVisible["steam"] = it }
                        FilterButton("Epic", storeVisible["epic"] == true, Modifier.weight(1f)) { storeVisible["epic"] = it }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterButton("GOG", storeVisible["gog"] == true, Modifier.weight(1f)) { storeVisible["gog"] = it }
                        FilterButton("Amazon", storeVisible["amazon"] == true, Modifier.weight(1f)) { storeVisible["amazon"] = it }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("CONTENT TYPES", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterButton("Games", contentFilters["games"] == true, Modifier.weight(1f)) { contentFilters["games"] = it }
                        FilterButton("DLC", contentFilters["dlc"] == true, Modifier.weight(1f)) { contentFilters["dlc"] = it }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterButton("Applications", contentFilters["applications"] == true, Modifier.weight(1f)) { contentFilters["applications"] = it }
                        FilterButton("Tools", contentFilters["tools"] == true, Modifier.weight(1f)) { contentFilters["tools"] = it }
                    }
                }
            }
        }
    }

    @Composable
    private fun FilterButton(label: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
        val bgColor = if (checked) Accent.copy(alpha = 0.2f) else CardDark
        val borderColor = if (checked) Accent else Color.Transparent
        val textColor = if (checked) Accent else TextSecondary

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable { onToggle(!checked) }
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
