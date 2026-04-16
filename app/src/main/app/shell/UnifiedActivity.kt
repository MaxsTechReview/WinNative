package com.winlator.cmod.app.shell

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.DocumentsContract
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.R
import com.winlator.cmod.app.PluviaApp
import com.winlator.cmod.app.db.PluviaDatabase
import com.winlator.cmod.app.service.DownloadService
import com.winlator.cmod.app.update.UpdateChecker
import com.winlator.cmod.feature.settings.InputControlsFragment
import com.winlator.cmod.feature.settings.SettingsHost
import com.winlator.cmod.feature.settings.SettingsNavItem
import com.winlator.cmod.feature.setup.SetupWizardActivity
import com.winlator.cmod.feature.shortcuts.LibraryShortcutUtils
import com.winlator.cmod.feature.shortcuts.ShortcutBroadcastReceiver
import com.winlator.cmod.feature.shortcuts.ShortcutSettingsComposeDialog
import com.winlator.cmod.feature.shortcuts.ShortcutsFragment
import com.winlator.cmod.feature.stores.epic.data.EpicCredentials
import com.winlator.cmod.feature.stores.epic.data.EpicGame
import com.winlator.cmod.feature.stores.epic.data.EpicGameToken
import com.winlator.cmod.feature.stores.epic.service.EpicAuthManager
import com.winlator.cmod.feature.stores.epic.service.EpicCloudSavesManager
import com.winlator.cmod.feature.stores.epic.service.EpicConstants
import com.winlator.cmod.feature.stores.epic.service.EpicDownloadManager
import com.winlator.cmod.feature.stores.epic.service.EpicGameLauncher
import com.winlator.cmod.feature.stores.epic.service.EpicManager
import com.winlator.cmod.feature.stores.epic.service.EpicService
import com.winlator.cmod.feature.stores.epic.ui.auth.EpicOAuthActivity
import com.winlator.cmod.feature.stores.gog.data.GOGGame
import com.winlator.cmod.feature.stores.gog.data.LibraryItem
import com.winlator.cmod.feature.stores.gog.service.GOGAuthManager
import com.winlator.cmod.feature.stores.gog.service.GOGConstants
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.feature.stores.gog.ui.auth.GOGOAuthActivity
import com.winlator.cmod.feature.stores.steam.SteamLoginActivity
import com.winlator.cmod.feature.stores.steam.data.DepotInfo
import com.winlator.cmod.feature.stores.steam.data.DownloadInfo
import com.winlator.cmod.feature.stores.steam.data.SteamApp
import com.winlator.cmod.feature.stores.steam.enums.DownloadPhase
import com.winlator.cmod.feature.stores.steam.events.AndroidEvent
import com.winlator.cmod.feature.stores.steam.events.EventDispatcher
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.feature.stores.steam.utils.getAvatarURL
import com.winlator.cmod.feature.sync.google.CloudSyncManager
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.runtime.display.XServerDisplayActivity
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.runtime.input.ControllerHelper
import com.winlator.cmod.runtime.wine.PeIconExtractor
import com.winlator.cmod.shared.android.ActivityResultHost
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.android.RefreshRateUtils
import com.winlator.cmod.shared.io.StorageUtils
import com.winlator.cmod.shared.ui.CarouselView
import com.winlator.cmod.shared.ui.FourByTwoGridView
import com.winlator.cmod.shared.ui.JoystickCarouselScroll
import com.winlator.cmod.shared.ui.JoystickGridScroll
import com.winlator.cmod.shared.ui.JoystickListScroll
import com.winlator.cmod.shared.ui.ListView
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import com.winlator.cmod.shared.ui.widget.chasingBorder
import dagger.hilt.android.AndroidEntryPoint
import `in`.dragonbra.javasteam.enums.EPersonaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

// Color palette
private val BgDark = Color(0xFF18181D)
private val SurfaceDark = Color(0xFF1E252E)
private val CardDark = Color(0xFF12121B)
private val CardBorder = Color(0xFF2A2A3A)
private val Accent = Color(0xFF1A9FFF)
private val AccentGlow = Color(0xFF58A6FF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)
private val DangerRed = Color(0xFFFF6B6B)
private val StatusOnline = Color(0xFF3FB950)
private val StatusAway = Color(0xFFF0C040)
private val StatusOffline = Color(0xFF6E7681)
private val TabScreenHorizontalPadding = 16.dp
private val TabScreenBottomPadding = 8.dp
private val UnifiedTopBarHorizontalPadding = 8.dp
private val UnifiedTopBarTopPadding = 4.dp
private val UnifiedTopBarHeight = 56.dp
private val TabListContentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
private val TabGridContentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
private val TabGridTopPadding = 8.dp
private val TabCarouselTopPadding = 12.dp
private val TabCarouselBottomPadding = 20.dp
private val DownloadsHeaderTopPadding = 2.dp

private fun Modifier.tabScreenPadding(
    top: Dp = 0.dp,
    bottom: Dp = TabScreenBottomPadding,
): Modifier = padding(start = TabScreenHorizontalPadding, top = top, end = TabScreenHorizontalPadding, bottom = bottom)

private val LIBRARY_NAME_SANITIZE_REGEX = "[^A-Za-z0-9 _-]".toRegex()

enum class LibraryLayoutMode {
    GRID_4,
    CAROUSEL,
    LIST,
}

@AndroidEntryPoint
class UnifiedActivity :
    AppCompatActivity(),
    ActivityResultHost {
    @Inject lateinit var db: PluviaDatabase

    private data class PendingNavigation(
        val item: SettingsNavItem = SettingsNavItem.CONTAINERS,
        val profileId: Int = 0,
        val editContainerId: Int = 0,
    )

    // Root navigation controller for hub <-> settings transitions
    private var rootNavController: NavHostController? = null

    // Queued navigation to process once the nav controller is ready
    private var pendingNavigation: PendingNavigation? = null

    // Guards against rapid Back presses during the settings → hub exit animation.
    // Without this, two popBackStack() calls inside the 300ms transition can desync
    // the NavHost state and leave the root composable rendering nothing (black screen).
    private var isPoppingSettings: Boolean = false

    // Track the currently selected game in the carousel for Game Settings button
    private var selectedSteamAppId: Int = 0
    private var selectedSteamAppName: String = ""
    private var selectedLibrarySource: String = ""
    private var selectedGogGameId: String = ""

    // Trigger to refresh library when activity resumes from another container
    var libraryRefreshSignal by mutableIntStateOf(0)
    private var hasBootstrappedGoogleRestoreOnHome = false

    // Freezes the library/store card chasing borders while any full-screen
    // dialog is open, so the ~120 Hz animation cost isn't paid for content
    // the user can't see or interact with.
    private val chasingBordersPaused = mutableStateOf(false)

    // LibraryCarousel is always composed (kept alive behind an alpha(0f) when
    // another tab is active). This flag lets GameCapsule skip its animation
    // while the library is invisible.
    private val libraryTabActive = mutableStateOf(true)

    val rightStickScrollState = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val leftStickScrollState = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val leftStickXState = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val keyEventFlow = kotlinx.coroutines.flow.MutableSharedFlow<android.view.KeyEvent>(extraBufferCapacity = 10)

    // Library grid focus: tracked index and item count, controlled by DPAD
    val libraryFocusIndex = kotlinx.coroutines.flow.MutableStateFlow(0)
    var libraryItemCount: Int = 0
    private var currentLibraryLayoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID_4

    // Store grid focus: same pattern for store/steam/epic/gog tabs
    val storeFocusIndex = kotlinx.coroutines.flow.MutableStateFlow(0)
    var storeItemCount: Int = 0
    private var storeColumns: Int = 4

    // Reference to the active store tab's grid state so we can snap focus to visible area
    var storeGridState: androidx.compose.foundation.lazy.grid.LazyGridState? = null

    // Single shared gate for ALL navigation inputs (dpad keys, hat axes, joystick)
    // so that simultaneous events from the same physical input don't cause double moves.
    private var lastMoveTime = 0L

    // Tracks whether a d-pad direction is currently held so we can distinguish
    // a fresh press (fires immediately) from a held repeat (throttled at 250ms).
    private var dpadHeld = false
    private var joystickActive = false

    companion object {
        private const val MOVE_INTERVAL_MS = 250L
        private var instance: UnifiedActivity? = null

        fun refreshLibrary() {
            instance?.let { it.libraryRefreshSignal++ }
        }
    }

    private val wallpaperImagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult

            val bitmap =
                com.winlator.cmod.shared.android.ImageUtils
                    .getBitmapFromUri(this, uri, 1280)
            if (bitmap != null) {
                val wallpaperFile =
                    com.winlator.cmod.runtime.wine.WineThemeManager
                        .getUserWallpaperFile(this)
                com.winlator.cmod.shared.android.ImageUtils
                    .save(bitmap, wallpaperFile, Bitmap.CompressFormat.PNG, 100)
            }
        }

    private val driveAuthLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            GameSaveBackupManager.onDriveAuthResult(this, result.resultCode)
        }

    override fun launchWallpaperImagePicker() {
        wallpaperImagePickerLauncher.launch("image/*")
    }

    override fun launchDriveAuthRequest(intentSender: IntentSender) {
        driveAuthLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
    }

    private fun moveLibraryFocus(
        left: Boolean,
        right: Boolean,
        up: Boolean,
        down: Boolean,
    ) {
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

            LibraryLayoutMode.LIST -> {
                if (up) newIdx = (idx - 1).coerceAtLeast(0)
                if (down) newIdx = (idx + 1).coerceAtMost(count - 1)
            }
        }
        libraryFocusIndex.value = newIdx
    }

    private fun moveStoreFocus(
        left: Boolean,
        right: Boolean,
        up: Boolean,
        down: Boolean,
    ) {
        val count = storeItemCount
        if (count <= 0) return
        val cols = storeColumns

        // If the current focus index is not visible (e.g. user scrolled with right joystick),
        // snap focus to the top-left of the visible area first.
        var idx = storeFocusIndex.value
        val grid = storeGridState
        if (grid != null) {
            val visibleItems = grid.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val firstVisible = visibleItems.first().index
                val lastVisible = visibleItems.last().index
                if (idx < firstVisible || idx > lastVisible) {
                    idx = firstVisible
                    storeFocusIndex.value = idx
                    return // just snap, don't move further this press
                }
            }
        }

        var newIdx = idx
        if (left) newIdx = (idx - 1).coerceAtLeast(0)
        if (right) newIdx = (idx + 1).coerceAtMost(count - 1)
        if (up) newIdx = (idx - cols).coerceAtLeast(0)
        if (down) newIdx = (idx + cols).coerceAtMost(count - 1)
        storeFocusIndex.value = newIdx
    }

    private fun gogPseudoId(gameId: String): Int {
        val normalized = gameId.hashCode() and 0x1FFFFFFF
        return 1_500_000_000 + normalized
    }

    // Cached reference to avoid fragment tree traversal on every input event.
    // Invalidated via FragmentLifecycleCallbacks.
    private var cachedInputControlsFragment: InputControlsFragment? = null
    private val inputControlsFragmentTracker =
        object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(
                fm: androidx.fragment.app.FragmentManager,
                f: androidx.fragment.app.Fragment,
            ) {
                if (f is InputControlsFragment) cachedInputControlsFragment = f
            }

            override fun onFragmentPaused(
                fm: androidx.fragment.app.FragmentManager,
                f: androidx.fragment.app.Fragment,
            ) {
                if (f is InputControlsFragment) cachedInputControlsFragment = null
            }
        }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Forward to InputControlsFragment if it's active (for gamepad binding capture)
        cachedInputControlsFragment?.let { fragment ->
            if (fragment.dispatchKeyEvent(event)) return true
        }

        val keyCode = event.keyCode
        val action = event.action

        // Intercept keys we handle globally to prevent fall-through (e.g. Start button launching a game)
        val isHandledGlobally =
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_BUTTON_START,
                android.view.KeyEvent.KEYCODE_BUTTON_A,
                android.view.KeyEvent.KEYCODE_BUTTON_B,
                android.view.KeyEvent.KEYCODE_BUTTON_X,
                android.view.KeyEvent.KEYCODE_BUTTON_Y,
                android.view.KeyEvent.KEYCODE_BUTTON_L1,
                android.view.KeyEvent.KEYCODE_BUTTON_R1,
                android.view.KeyEvent.KEYCODE_BUTTON_L2,
                android.view.KeyEvent.KEYCODE_BUTTON_R2,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                -> true

                else -> false
            }

        // Intercept DPAD events on all tabs for throttled, grid-aware navigation
        val isDpad =
            keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
        if (isDpad) {
            if (action == android.view.KeyEvent.ACTION_UP) {
                // Release: allow next press to fire immediately
                dpadHeld = false
                return true
            }
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                val now = android.os.SystemClock.uptimeMillis()
                // Fresh press fires immediately; held repeat is throttled at 250ms
                if (!dpadHeld || (now - lastMoveTime >= MOVE_INTERVAL_MS)) {
                    val left = keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    val right = keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    val up = keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                    val down = keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    when (currentTabKey) {
                        "library" -> moveLibraryFocus(left, right, up, down)
                        else -> moveStoreFocus(left, right, up, down)
                    }
                    lastMoveTime = now
                    dpadHeld = true
                }
            }
            return true // consume both DOWN and UP
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

    override fun onPause() {
        super.onPause()
        chasingBordersPaused.value = true
    }

    override fun onResume() {
        super.onResume()
        chasingBordersPaused.value = false
        // Ensure all store services are running when returning from a game or other activity
        if (GOGService.hasStoredCredentials(this) && !GOGService.isRunning) {
            GOGService.start(this)
        }
        if (EpicService.hasStoredCredentials(this) && !EpicService.isRunning) {
            EpicService.start(this)
        }
        if (SteamService.hasStoredCredentials(this) && SteamService.instance == null) {
            SteamService.start(this)
        }
        libraryRefreshSignal++

        // (Re)start the background update loop (checks hourly + on first tick)
        UpdateChecker.startBackgroundLoop(this)

        lifecycleScope.launch {
            bootstrapGoogleRestoreOnFirstHomeArrival()
        }
    }

    private suspend fun bootstrapGoogleRestoreOnFirstHomeArrival() {
        if (hasBootstrappedGoogleRestoreOnHome) return
        hasBootstrappedGoogleRestoreOnHome = true
        CloudSyncManager.bootstrapOnHomeScreenArrival(this)
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        // Forward to InputControlsFragment if it's active (for gamepad binding capture)
        cachedInputControlsFragment?.let { fragment ->
            if (fragment.dispatchGenericMotionEvent(event)) return true
        }

        if ((event.source and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK &&
            event.action == android.view.MotionEvent.ACTION_MOVE
        ) {
            // Handle Right Joystick Y axis for scrolling in stores
            val rz = event.getAxisValue(android.view.MotionEvent.AXIS_RZ)
            rightStickScrollState.value = rz

            // Handle Left Joystick Y axis for scrolling in stores
            val leftY = event.getAxisValue(android.view.MotionEvent.AXIS_Y)
            leftStickScrollState.value = leftY

            // Handle Left Joystick X axis for carousel horizontal scroll
            val leftX = event.getAxisValue(android.view.MotionEvent.AXIS_X)
            leftStickXState.value = leftX

            // Handle Left Joystick/D-pad for grid navigation on all tabs
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

            val anyDirection =
                isHatLeft || isHatRight || isHatUp || isHatDown ||
                    isJoystickLeft || isJoystickRight || isJoystickUp || isJoystickDown

            if (anyDirection) {
                if (now - lastMoveTime >= MOVE_INTERVAL_MS) {
                    val left = isHatLeft || isJoystickLeft
                    val right = isHatRight || isJoystickRight
                    val up = isHatUp || isJoystickUp
                    val down = isHatDown || isJoystickDown
                    when (currentTabKey) {
                        "library" -> moveLibraryFocus(left, right, up, down)
                        else -> moveStoreFocus(left, right, up, down)
                    }
                    lastMoveTime = now
                    joystickActive = true
                }
                return true
            } else if (joystickActive) {
                // Joystick returned to center — reset so next flick fires immediately
                joystickActive = false
                lastMoveTime = 0L
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private var currentTabKey: String = "library"

    // Callback set by the active store tab so the A-button handler can trigger a click on the focused item
    var storeItemClickCallback: ((Int) -> Unit)? = null

    private fun injectKeyEvent(keyCode: Int) {
        window.decorView.rootView.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        window.decorView.rootView.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    private fun reapplyPreferredRefreshRate() {
        if (isFinishing || isDestroyed) return
        RefreshRateUtils.applyPreferredRefreshRate(this)
    }

    private fun navigateToSettings(
        item: SettingsNavItem = SettingsNavItem.CONTAINERS,
        profileId: Int = 0,
        editContainerId: Int = 0,
    ) {
        // Settings is an in-activity navigation target, so entering it does not trigger an
        // Activity resume. Reassert the preferred display mode at the activity boundary.
        reapplyPreferredRefreshRate()
        val route = buildSettingsRoute(item, profileId, editContainerId)
        val nav = rootNavController
        if (nav == null) {
            pendingNavigation = PendingNavigation(item, profileId, editContainerId)
            return
        }
        isPoppingSettings = false
        nav.navigate(route) {
            launchSingleTop = true
        }
    }

    private fun buildSettingsRoute(
        item: SettingsNavItem = SettingsNavItem.CONTAINERS,
        profileId: Int = 0,
        editContainerId: Int = 0,
    ): String = "settings?item=${item.name}&profileId=$profileId&editContainerId=$editContainerId"

    private fun extractSettingsNavigation(intent: Intent?): PendingNavigation? {
        if (intent == null) return null

        val editContainerId = intent.getIntExtra("edit_container_id", 0)
        if (editContainerId > 0) {
            return PendingNavigation(SettingsNavItem.CONTAINERS, 0, editContainerId)
        }

        if (intent.getBooleanExtra("edit_input_controls", false)) {
            val profileId = intent.getIntExtra("selected_profile_id", 0)
            return PendingNavigation(SettingsNavItem.INPUT_CONTROLS, profileId, 0)
        }

        val selectedMenuItemId = intent.getIntExtra("selected_menu_item_id", 0)
        if (selectedMenuItemId > 0) {
            val target = SettingsNavItem.fromMenuId(selectedMenuItemId) ?: SettingsNavItem.CONTAINERS
            return PendingNavigation(target, 0, 0)
        }

        return null
    }

    private fun consumeSettingsIntent(intent: Intent?) {
        intent ?: return
        intent.removeExtra("edit_container_id")
        intent.removeExtra("edit_input_controls")
        intent.removeExtra("selected_profile_id")
        intent.removeExtra("selected_menu_item_id")
    }

    private fun handleSettingsIntent(intent: Intent?) {
        val request = extractSettingsNavigation(intent) ?: return
        consumeSettingsIntent(intent)
        navigateToSettings(request.item, request.profileId, request.editContainerId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSettingsIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        instance = this
        super.onCreate(savedInstanceState)

        if (!SetupWizardActivity.isSetupComplete(this) || !ImageFs.find(this).isValid) {
            startActivity(
                Intent(this, SetupWizardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
            com.winlator.cmod.shared.android.AppUtils
                .applyOpenActivityTransition(this, 0, 0)
            finish()
            return
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(inputControlsFragmentTracker, true)
        db = PluviaDatabase.getInstance(this)
        EpicAuthManager.updateLoginStatus(this)
        GOGAuthManager.updateLoginStatus(this)
        GOGConstants.init(this)
        SteamService.initLoginStatus(this)

        // Start EpicService if user is logged in
        if (EpicService.hasStoredCredentials(this)) {
            EpicService.start(this)
        }

        // Start SteamService if user is logged in
        if (SteamService.hasStoredCredentials(this)) {
            SteamService.start(this)
        }

        if (GOGAuthManager.isLoggedIn(this)) {
            GOGService.start(this)
        }

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(0xFF141B24.toInt()),
        )
        val initialSettingsNavigation = extractSettingsNavigation(intent)
        if (initialSettingsNavigation != null) {
            consumeSettingsIntent(intent)
        }

        // Exclude left edge from system back gesture so the drawer can capture swipes
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.decorView.post {
                val leftEdgeWidth = (40 * resources.displayMetrics.density).toInt()
                val exclusionRect = android.graphics.Rect(0, 0, leftEdgeWidth, window.decorView.height)
                window.decorView.systemGestureExclusionRects = listOf(exclusionRect)
            }
        }

        // Start the background update loop (first check fires after 5 s)
        UpdateChecker.startBackgroundLoop(this)

        setContent {
            val navController = rememberNavController()
            rootNavController = navController

            // Drain any queued navigation or process the launch intent
            LaunchedEffect(Unit) {
                val pending = pendingNavigation
                if (pending != null) {
                    navigateToSettings(pending.item, pending.profileId, pending.editContainerId)
                    pendingNavigation = null
                } else {
                    handleSettingsIntent(intent)
                }
            }

            MaterialTheme(
                colorScheme =
                    darkColorScheme(
                        primary = Accent,
                        background = BgDark,
                        surface = SurfaceDark,
                        onSurface = TextPrimary,
                    ),
            ) {
                NavHost(
                    navController = navController,
                    startDestination =
                        initialSettingsNavigation?.let {
                            buildSettingsRoute(it.item, it.profileId, it.editContainerId)
                        } ?: "hub",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        val fromRoute = initialState.destination.route
                        val toRoute = targetState.destination.route
                        if (
                            (
                                (fromRoute == "hub" && toRoute?.startsWith("settings") == true) ||
                                    (fromRoute?.startsWith("settings") == true && toRoute == "hub")
                            )
                        ) {
                            fadeIn(tween(220, easing = FastOutSlowInEasing))
                        } else {
                            EnterTransition.None
                        }
                    },
                    exitTransition = {
                        val fromRoute = initialState.destination.route
                        val toRoute = targetState.destination.route
                        if (
                            (
                                (fromRoute == "hub" && toRoute?.startsWith("settings") == true) ||
                                    (fromRoute?.startsWith("settings") == true && toRoute == "hub")
                            )
                        ) {
                            fadeOut(tween(220, easing = FastOutSlowInEasing))
                        } else {
                            ExitTransition.None
                        }
                    },
                    popEnterTransition = {
                        val fromRoute = initialState.destination.route
                        val toRoute = targetState.destination.route
                        if (
                            (
                                (fromRoute == "hub" && toRoute?.startsWith("settings") == true) ||
                                    (fromRoute?.startsWith("settings") == true && toRoute == "hub")
                            )
                        ) {
                            fadeIn(tween(220, easing = FastOutSlowInEasing))
                        } else {
                            EnterTransition.None
                        }
                    },
                    popExitTransition = {
                        val fromRoute = initialState.destination.route
                        val toRoute = targetState.destination.route
                        if (
                            (
                                (fromRoute == "hub" && toRoute?.startsWith("settings") == true) ||
                                    (fromRoute?.startsWith("settings") == true && toRoute == "hub")
                            )
                        ) {
                            fadeOut(tween(220, easing = FastOutSlowInEasing))
                        } else {
                            ExitTransition.None
                        }
                    },
                ) {
                    composable("hub") {
                        // Once hub is the current destination, the previous settings-pop is
                        // complete — clear the guard so the next settings session starts fresh.
                        LaunchedEffect(Unit) { isPoppingSettings = false }
                        UnifiedHub()
                    }
                    composable(
                        "settings?item={item}&profileId={profileId}&editContainerId={editContainerId}",
                        arguments =
                            listOf(
                                navArgument("item") {
                                    type = NavType.StringType
                                    defaultValue = SettingsNavItem.CONTAINERS.name
                                },
                                navArgument("profileId") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                },
                                navArgument("editContainerId") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                },
                            ),
                    ) { backStackEntry ->
                        val itemName = backStackEntry.arguments?.getString("item") ?: SettingsNavItem.CONTAINERS.name
                        val startItem =
                            try {
                                SettingsNavItem.valueOf(itemName)
                            } catch (_: Exception) {
                                SettingsNavItem.CONTAINERS
                            }
                        val profileId = backStackEntry.arguments?.getInt("profileId") ?: 0
                        val editContainerId = backStackEntry.arguments?.getInt("editContainerId") ?: 0

                        // Idempotent pop: the first Back press pops, any further presses during
                        // the 220ms exit animation are absorbed here so NavHost state stays
                        // consistent (see isPoppingSettings field for full context).
                        val popSettingsOnce: () -> Unit = {
                            if (!isPoppingSettings) {
                                isPoppingSettings = true
                                navController.popBackStack()
                            }
                        }
                        BackHandler(enabled = true) { popSettingsOnce() }

                        SettingsHost(
                            startItem = startItem,
                            selectedProfileId = profileId,
                            bordersPaused = chasingBordersPaused.value,
                            onBack = popSettingsOnce,
                        )

                        // Handle edit_container_id deep link — show dialog on main thread outside composition
                        if (editContainerId > 0) {
                            LaunchedEffect(editContainerId) {
                                val activity = this@UnifiedActivity
                                val cm = ContainerManager(activity)
                                val container = cm.getContainerById(editContainerId)
                                if (container != null) {
                                    com.winlator.cmod.feature.settings
                                        .ContainerSettingsComposeDialog(
                                            activity,
                                            container,
                                        ) { navController.popBackStack() }
                                        .show()
                                } else {
                                    navController.popBackStack()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Tab definitions
    private data class TabDef(
        val label: String,
        val key: String,
    )

    private fun buildTabs(storeVisible: Map<String, Boolean>): List<TabDef> {
        val base =
            mutableListOf(
                TabDef(getString(R.string.common_ui_library), "library"),
                TabDef(getString(R.string.common_ui_downloads), "downloads"),
            )
        if (storeVisible["steam"] != false) base.add(TabDef("Steam", "steam"))
        if (storeVisible["epic"] != false) base.add(TabDef("Epic", "epic"))
        if (storeVisible["gog"] != false) base.add(TabDef("GOG", "gog"))
        return base
    }

    // Main scaffold
    @Composable
    fun UnifiedHub() {
        val storeVisible = remember { mutableStateMapOf("steam" to true, "epic" to true, "gog" to true) }
        var showAddCustomGame by remember { mutableStateOf(false) }
        var showExitDialog by remember { mutableStateOf(false) }
        var searchQueryTfv by remember { mutableStateOf(TextFieldValue("")) }
        val searchQuery = searchQueryTfv.text
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
                    .getOrDefault(LibraryLayoutMode.GRID_4),
            )
        }
        val tabs = remember(storeVisible.toMap()) { buildTabs(storeVisible) }
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
            while (true) {
                isControllerConnected = ControllerHelper.isControllerConnected()
                kotlinx.coroutines.delay(2000)
            }
        }

        // Observe library install status changes to refresh UI
        LaunchedEffect(Unit) {
            val listener =
                object : EventDispatcher.JavaEventListener {
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

        val epicLoginLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val code = result.data?.getStringExtra(EpicOAuthActivity.EXTRA_AUTH_CODE)
                    if (code != null) {
                        scope.launch {
                            val authResult = EpicAuthManager.authenticateWithCode(context, code)
                            if (authResult.isSuccess) {
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    R.string.stores_accounts_logged_in_epic,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            } else {
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    getString(R.string.stores_accounts_epic_login_failed, authResult.exceptionOrNull()?.message),
                                    android.widget.Toast.LENGTH_LONG,
                                )
                            }
                        }
                    }
                }
            }

        val gogLoginLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val code = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_AUTH_CODE)
                    if (!code.isNullOrBlank()) {
                        scope.launch {
                            val authResult = GOGAuthManager.authenticateWithCode(context, code)
                            if (authResult.isSuccess) {
                                GOGService.start(context)
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    R.string.stores_accounts_logged_in_gog,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            } else {
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    getString(R.string.stores_accounts_gog_login_failed, authResult.exceptionOrNull()?.message),
                                    android.widget.Toast.LENGTH_LONG,
                                )
                            }
                        }
                    }
                }
            }

        val filteredSteamApps =
            remember(steamApps, contentFilters.toMap()) {
                steamApps.filter { app ->
                    when (app.type) {
                        com.winlator.cmod.feature.stores.steam.enums.AppType.game -> contentFilters["games"] == true
                        com.winlator.cmod.feature.stores.steam.enums.AppType.demo -> contentFilters["games"] == true
                        com.winlator.cmod.feature.stores.steam.enums.AppType.dlc -> contentFilters["dlc"] == true
                        com.winlator.cmod.feature.stores.steam.enums.AppType.application -> contentFilters["applications"] == true
                        com.winlator.cmod.feature.stores.steam.enums.AppType.tool -> contentFilters["tools"] == true
                        com.winlator.cmod.feature.stores.steam.enums.AppType.config -> contentFilters["tools"] == true
                        else -> contentFilters["games"] == true
                    }
                }
            }

        // Sync Steam states periodically without forcing full library recomposition
        LaunchedEffect(Unit) {
            while (true) {
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
                        navigateToSettings(SettingsNavItem.STORES)
                    }

                    android.view.KeyEvent.KEYCODE_BUTTON_X -> {
                        if (key != "downloads") {
                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                        }
                    }

                    android.view.KeyEvent.KEYCODE_BUTTON_B -> {
                        // Close menus in order, or show exit confirmation if none are open
                        if (drawerState.isOpen) {
                            drawerState.close()
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

                    android.view.KeyEvent.KEYCODE_BUTTON_Y -> {
                        if (key == "library" && (selectedSteamAppId != 0 || selectedGogGameId.isNotEmpty())) {
                            if (selectedLibrarySource == "GOG") {
                                globalSettingsGogGame = gogApps.find { it.id == selectedGogGameId }
                                return@collect
                            }
                            val isCustom = selectedSteamAppId < 0
                            val epicId = if (selectedSteamAppId >= 2000000000) selectedSteamAppId - 2000000000 else 0

                            // Handle Steam, Custom, and Epic semi-unified logic for the settings dialog trigger
                            globalSettingsApp = (
                                steamApps.find { it.id == selectedSteamAppId }
                                    ?: if (isCustom) {
                                        SteamApp(id = selectedSteamAppId, name = selectedSteamAppName, developer = "Custom")
                                    } else if (epicId > 0) {
                                        val epic = epicApps.find { it.id == epicId }
                                        SteamApp(
                                            id = selectedSteamAppId,
                                            name = selectedSteamAppName,
                                            developer = epic?.developer ?: "Epic Games",
                                            gameDir = epic?.installPath ?: "",
                                        )
                                    } else {
                                        null
                                    }
                            )
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
                                    val dummyApp =
                                        SteamApp(id = selectedSteamAppId, name = selectedSteamAppName, gameDir = epic.installPath)
                                    launchSteamGame(context, containerManager, dummyApp)
                                }
                            } else {
                                val steam = steamApps.find { it.id == selectedSteamAppId }
                                if (steam != null && SteamService.isAppInstalled(steam.id)) {
                                    launchSteamGame(context, containerManager, steam)
                                }
                            }
                        } else if (key != "library" && key != "downloads") {
                            // Store tabs: trigger click on focused item
                            storeItemClickCallback?.invoke(storeFocusIndex.value)
                        }
                    }

                    android.view.KeyEvent.KEYCODE_BUTTON_L2 -> {
                        if (key == "downloads") {
                            val pausableDownloads =
                                DownloadService.getAllDownloads().filter {
                                    val status = it.second.getStatusFlow().value
                                    status != DownloadPhase.COMPLETE && status != DownloadPhase.CANCELLED
                                }
                            if (pausableDownloads.isNotEmpty()) {
                                val allPausableDownloadsPaused =
                                    pausableDownloads.all {
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
                    storeVisible = storeVisible,
                    contentFilters = contentFilters,
                    libraryLayoutMode = libraryLayoutMode,
                    onLibraryLayoutSelected = {
                        libraryLayoutMode = it
                        PrefManager.libraryLayoutMode = it.name
                    },
                    onClose = { scope.launch { drawerState.close() } },
                )
            },
            scrimColor = Color.Black.copy(alpha = 0.5f),
            gesturesEnabled = true,
        ) {
            Box(Modifier.fillMaxSize().background(BgDark)) {
                Scaffold(
                    containerColor = BgDark,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        TopBar(tabs, selectedIdx, {
                            selectedIdx = it
                        }, persona, context, scope, isControllerConnected, isPS, isLibraryTab, searchQueryTfv, {
                            searchQueryTfv =
                                it
                        }, onFilterClicked = { scope.launch { drawerState.open() } }) {
                            if (selectedLibrarySource == "GOG") {
                                globalSettingsGogGame = gogApps.find { it.id == selectedGogGameId }
                            } else {
                                // Try Steam apps first, then fall back to custom or epic pseudo-apps
                                globalSettingsApp = (
                                    steamApps.find { it.id == selectedSteamAppId }
                                        ?: if (selectedSteamAppId < 0) {
                                            // Build a pseudo SteamApp for the custom game
                                            SteamApp(
                                                id = selectedSteamAppId,
                                                name = selectedSteamAppName,
                                                developer = "Custom",
                                            )
                                        } else if (selectedSteamAppId >= 2000000000) {
                                            val epicId = selectedSteamAppId - 2000000000
                                            val epic = epicApps.find { it.id == epicId }
                                            SteamApp(
                                                id = selectedSteamAppId,
                                                name = selectedSteamAppName,
                                                developer = epic?.developer ?: "Epic Games",
                                                gameDir = epic?.installPath ?: "",
                                            )
                                        } else {
                                            null
                                        }
                                )
                            }
                        }
                    },
                ) { padding ->
                    LaunchedEffect(selectedIdx, tabs) {
                        currentTabKey = tabs.getOrNull(selectedIdx)?.key ?: "library"
                        // Reset store focus when switching tabs
                        storeFocusIndex.value = 0
                        storeItemClickCallback = null
                    }

                    Box(Modifier.padding(padding).fillMaxSize().background(BgDark)) {
                        val key = tabs.getOrNull(selectedIdx)?.key ?: "library"

                        LaunchedEffect(key) { libraryTabActive.value = (key == "library") }

                        // Keep Library tab always composed so its state survives tab switches
                        Box(
                            Modifier.fillMaxSize().let {
                                if (key == "library") {
                                    it
                                } else {
                                    it.alpha(0f).pointerInput(Unit) { /* block ghost taps */ }
                                }
                            },
                        ) {
                            LibraryCarousel(
                                isLoggedIn = isLoggedIn,
                                steamApps = filteredSteamApps,
                                epicApps = epicApps,
                                gogApps = gogApps,
                                layoutMode = libraryLayoutMode,
                                libraryRefreshKey = libraryRefreshKey,
                                iconRefreshKey = iconRefreshKey,
                                searchQuery = searchQuery,
                            )
                        }

                        if (key != "library") {
                            AnimatedContent(
                                targetState = key,
                                transitionSpec = {
                                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                                },
                                label = "tabContent",
                            ) { animatedKey ->
                                when (animatedKey) {
                                    "downloads" -> {
                                        DownloadsTab(selectedDownloadId, onSelectDownload = { selectedDownloadId = it })
                                    }

                                    "steam" -> {
                                        SteamStoreTab(isLoggedIn, filteredSteamApps, searchQuery, libraryLayoutMode)
                                    }

                                    "epic" -> {
                                        EpicStoreTab(isEpicLoggedIn, searchQuery, libraryLayoutMode) {
                                            epicLoginLauncher.launch(Intent(this@UnifiedActivity, EpicOAuthActivity::class.java))
                                        }
                                    }

                                    "gog" -> {
                                        GOGStoreTab(isGogLoggedIn, searchQuery, libraryLayoutMode) {
                                            gogLoginLauncher.launch(Intent(this@UnifiedActivity, GOGOAuthActivity::class.java))
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }

                        val configuration = LocalConfiguration.current
                        val libraryFabBase = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
                        val addGameFabSize = (libraryFabBase * 0.125f).dp.coerceIn(56.dp, 64.dp)
                        val addGameFabMargin = (libraryFabBase * 0.035f).dp.coerceIn(12.dp, 20.dp)
                        val addGameFabIconSize = (libraryFabBase * 0.055f).dp.coerceIn(24.dp, 28.dp)

                        // Bottom-right Add Custom Game button
                        if (key == "library") {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .windowInsetsPadding(
                                            WindowInsets.navigationBars.only(
                                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                            ),
                                        )
                                        .padding(end = addGameFabMargin, bottom = addGameFabMargin)
                                        .size(addGameFabSize)
                                        .shadow(10.dp, CircleShape, spotColor = Accent.copy(alpha = 0.4f))
                                        .clip(CircleShape)
                                        .background(SurfaceDark.copy(alpha = 0.96f), CircleShape)
                                        .border(1.5.dp, Accent.copy(alpha = 0.55f), CircleShape)
                                        .focusProperties { canFocus = false } // No specific button for this, handle via long press or touch
                                        .clickable { showAddCustomGame = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = "Add Custom Game",
                                    tint = Color.White,
                                    modifier = Modifier.size(addGameFabIconSize),
                                )
                            }
                        }
                    }
                }
            }
        } // end ModalNavigationDrawer

        if (globalSettingsApp != null) {
            GameSettingsDialog(
                app = globalSettingsApp!!,
                onDismissRequest = { globalSettingsApp = null },
            )
        }
        if (globalSettingsGogGame != null) {
            GOGGameSettingsDialog(
                app = globalSettingsGogGame!!,
                onDismissRequest = { globalSettingsGogGame = null },
            )
        }

        if (showAddCustomGame) {
            AddCustomGameDialog(onDismiss = {
                showAddCustomGame = false
                libraryRefreshKey++
            })
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
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(320.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceDark)
                            .border(1.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.common_ui_exit_app_confirm),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // Cancel button
                            OutlinedButton(
                                onClick = { showExitDialog = false },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.common_ui_cancel), fontWeight = FontWeight.Medium)
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
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.common_ui_exit), color = Color.White, fontWeight = FontWeight.Bold)
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
        persona: com.winlator.cmod.feature.stores.steam.data.SteamFriend?,
        context: android.content.Context,
        scope: kotlinx.coroutines.CoroutineScope,
        isControllerConnected: Boolean,
        isPS: Boolean,
        isLibraryTab: Boolean,
        searchQuery: TextFieldValue,
        onSearchQueryChange: (TextFieldValue) -> Unit,
        onFilterClicked: () -> Unit,
        onGameSettingsClicked: () -> Unit,
    ) {
        var isSearchExpanded by remember { mutableStateOf(false) }
        val searchFocusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val isDownloadsTab = tabs.getOrNull(selectedIdx)?.key == "downloads"

        // Auto-collapse search when switching tabs
        LaunchedEffect(selectedIdx) {
            if (isSearchExpanded) {
                onSearchQueryChange(TextFieldValue(""))
                isSearchExpanded = false
            }
        }

        // Auto-focus the search field when expanded
        LaunchedEffect(isSearchExpanded) {
            if (isSearchExpanded) {
                kotlinx.coroutines.delay(150)
                searchFocusRequester.requestFocus()
            } else if (searchQuery.text.isNotEmpty()) {
                onSearchQueryChange(TextFieldValue(""))
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .padding(
                            start = UnifiedTopBarHorizontalPadding,
                            end = UnifiedTopBarHorizontalPadding,
                            top = UnifiedTopBarTopPadding,
                        )
                        .height(UnifiedTopBarHeight),
            ) {
                // Center Block: Tabs (absolutely centered, unaffected by left/right content)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isControllerConnected) {
                        ControllerBadge("L1")
                        Spacer(Modifier.width(8.dp))
                    }
                    @Suppress("DEPRECATION")
                    CompositionLocalProvider(
                        androidx.compose.material3.LocalRippleConfiguration provides null,
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
                            modifier =
                                Modifier
                                    .width(tabWidth * visibleCount)
                                    .height(44.dp)
                                    .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.5f))
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(CardDark)
                                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp)),
                        ) {
                            LazyRow(
                                state = tabListState,
                                flingBehavior = snapFlingBehavior,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .focusProperties { canFocus = !isLibraryTab },
                                userScrollEnabled = tabs.size > visibleCount,
                            ) {
                                itemsIndexed(tabs) { index, tab ->
                                    val selected = selectedIdx == index
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val tabScale by animateFloatAsState(
                                        targetValue = if (isPressed) 0.92f else 1f,
                                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                                        label = "tabScale",
                                    )
                                    val textColor by animateColorAsState(
                                        targetValue = if (selected) Accent else TextSecondary,
                                        animationSpec = tween(280),
                                        label = "tabTextColor",
                                    )

                                    Box(
                                        modifier =
                                            Modifier
                                                .width(tabWidth)
                                                .fillMaxHeight()
                                                .focusProperties { canFocus = false }
                                                .graphicsLayer {
                                                    scaleX = tabScale
                                                    scaleY = tabScale
                                                }.clickable(
                                                    interactionSource = interactionSource,
                                                    indication = null,
                                                ) { onSelect(index) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = tab.label.uppercase(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            color = textColor,
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Settings Button
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                                .clip(CircleShape)
                                .background(SurfaceDark)
                                .focusProperties { canFocus = !isLibraryTab },
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = {
                            navigateToSettings(SettingsNavItem.STORES)
                        }, modifier = Modifier.size(44.dp), enabled = true) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Menu", tint = TextPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                    if (isControllerConnected) {
                        Spacer(Modifier.width(8.dp))
                        ControllerBadge(if (isPS) "\u2261" else "Start")
                    }

                    // Search Button (disabled on downloads tab)
                    Spacer(Modifier.width(12.dp))

                    val searchIconRotation by animateFloatAsState(
                        targetValue = if (isSearchExpanded) 90f else 0f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        label = "searchIconRotation",
                    )

                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                                .clip(CircleShape)
                                .background(
                                    if (isDownloadsTab) {
                                        SurfaceDark.copy(alpha = 0.4f)
                                    } else if (isSearchExpanded) {
                                        Accent.copy(alpha = 0.15f)
                                    } else {
                                        SurfaceDark
                                    },
                                ).focusProperties { canFocus = !isLibraryTab },
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = {
                                if (!isDownloadsTab) {
                                    if (isSearchExpanded) {
                                        onSearchQueryChange(TextFieldValue(""))
                                        isSearchExpanded = false
                                    } else {
                                        isSearchExpanded = true
                                    }
                                }
                            },
                            modifier = Modifier.size(44.dp),
                            enabled = !isDownloadsTab,
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint =
                                    if (isDownloadsTab) {
                                        TextSecondary.copy(alpha = 0.4f)
                                    } else if (isSearchExpanded) {
                                        Accent
                                    } else {
                                        TextPrimary
                                    },
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .graphicsLayer { rotationZ = searchIconRotation },
                            )
                        }
                    }
                }

                // Right Block: Status & Actions
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isStore = tabs.getOrNull(selectedIdx)?.label?.contains("Store", ignoreCase = true) == true
                    if (isControllerConnected && !isStore) {
                        ControllerBadge(if (isPS) "\u25B3" else "Y")
                        Spacer(Modifier.width(8.dp))
                    }

                    Spacer(Modifier.width(8.dp))

                    // Filter button (opens drawer)
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                                .clip(CircleShape)
                                .background(SurfaceDark)
                                .focusProperties { canFocus = !isLibraryTab }
                                .clickable { onFilterClicked() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filter", tint = TextPrimary, modifier = Modifier.size(24.dp))
                    }
                    if (isControllerConnected) {
                        Spacer(Modifier.width(8.dp))
                        ControllerBadge(if (isPS) "\u25A1" else "X")
                    }
                }
            }

            // Dropdown Search Bar
            AnimatedVisibility(
                visible = isSearchExpanded && !isDownloadsTab,
                enter =
                    expandVertically(
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(animationSpec = tween(200)),
                exit =
                    shrinkVertically(
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(animationSpec = tween(120)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth(0.7f)
                                .height(44.dp)
                                .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.4f))
                                .clip(RoundedCornerShape(24.dp))
                                .background(SurfaceDark),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle =
                                    TextStyle(
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                    ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                                cursorBrush = Brush.verticalGradient(listOf(Accent, AccentGlow)),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(searchFocusRequester),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.text.isEmpty()) {
                                            Text(
                                                "Search games...",
                                                style =
                                                    TextStyle(
                                                        color = TextSecondary,
                                                        fontSize = 15.sp,
                                                    ),
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            if (searchQuery.text.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange(TextFieldValue("")) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Clear",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp),
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
                    val apps =
                        allShortcuts
                            .mapNotNull { shortcut ->
                                if (!LibraryShortcutUtils.isCustomLibraryShortcut(shortcut)) {
                                    return@mapNotNull null
                                }

                                val displayName =
                                    shortcut
                                        .getExtra("custom_name", shortcut.name)
                                        .ifBlank { shortcut.name }
                                val customId = -(displayName.hashCode().and(0x7FFFFFFF) + 1)

                                SteamApp(
                                    id = customId,
                                    name = displayName,
                                    developer = "Custom",
                                    gameDir =
                                        shortcut.getExtra(
                                            "game_install_path",
                                            shortcut.getExtra("custom_game_folder", ""),
                                        ),
                                )
                            }
                    withContext(Dispatchers.Main) {
                        cachedShortcuts = allShortcuts
                        customApps = apps
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Move expensive filtering (runBlocking DB queries, file I/O) off the main thread
        var installedApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        var gogByPseudoId by remember { mutableStateOf<Map<Int, GOGGame>>(emptyMap()) }
        var libraryLoaded by remember { mutableStateOf(false) }
        // Track whether the LaunchedEffect is still reprocessing after input changes
        // so we don't flash the empty state while filtering is in progress.
        var inputVersion by remember { mutableIntStateOf(0) }
        var processedVersion by remember { mutableIntStateOf(0) }

        LaunchedEffect(steamApps, epicApps, gogApps, customApps, libraryRefreshKey) {
            inputVersion++
            // On first run, wait for DB to emit before declaring loaded
            if (!libraryLoaded) {
                db.steamAppDao().getAllOwnedApps().first()
            }
            withContext(Dispatchers.IO) {
                val steamInstalled = steamApps.filter { SteamService.isAppInstalled(it.id) }

                val epicInstalled = epicApps.filter { it.isInstalled }

                val gogInstalled = gogApps.filter { it.isInstalled && java.io.File(it.installPath).exists() }

                val gogMap = gogInstalled.associateBy { gogPseudoId(it.id) }

                val playtimePrefs = context.getSharedPreferences("playtime_stats", android.content.Context.MODE_PRIVATE)
                val allPlaytime = playtimePrefs.all
                val mappedEpic =
                    epicInstalled.map { epic ->
                        SteamApp(
                            id = 2000000000 + epic.id,
                            name = epic.title,
                            developer = epic.developer,
                            gameDir = epic.installPath,
                        )
                    }
                val mappedGog =
                    gogInstalled.map { gog ->
                        SteamApp(
                            id = gogPseudoId(gog.id),
                            name = gog.title,
                            developer = gog.developer,
                            gameDir = gog.installPath,
                        )
                    }
                val merged = steamInstalled + customApps + mappedEpic + mappedGog
                val sorted =
                    merged.sortedByDescending { app ->
                        val searchKey =
                            if (app.id >= 2000000000 || app.id < 0) {
                                app.name
                            } else {
                                app.name.replace(LIBRARY_NAME_SANITIZE_REGEX, "")
                            }
                        (allPlaytime["${searchKey}_last_played"] as? Long) ?: 0L
                    }

                withContext(Dispatchers.Main) {
                    gogByPseudoId = gogMap
                    installedApps = sorted
                    libraryLoaded = true
                    processedVersion = inputVersion
                }
            }
        }

        val displayedApps =
            remember(installedApps, searchQuery) {
                if (searchQuery.isBlank()) {
                    installedApps
                } else {
                    installedApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }
            }

        // Show a loading spinner until the first library scan completes,
        // with a minimum display time so it doesn't flash
        var minTimeElapsed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(500)
            minTimeElapsed = true
        }
        // A logged-in store whose owned-apps list is still empty hasn't finished
        // its initial library fetch yet — keep the spinner up instead of flashing
        // "No games installed". This resolves itself once the store populates its
        // DB (steamApps/epicApps/gogApps become non-empty) or if other sources
        // (custom apps, other stores) already have installed games.
        val awaitingStoreSync =
            installedApps.isEmpty() && (
                (isLoggedIn && steamApps.isEmpty()) ||
                    (epicApps.isEmpty() && EpicService.hasStoredCredentials(context)) ||
                    (gogApps.isEmpty() && GOGAuthManager.isLoggedIn(context))
            )
        val showLoading = !libraryLoaded || !minTimeElapsed || processedVersion < inputVersion || awaitingStoreSync
        if (showLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val spinAlpha by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 600),
                    label = "loaderFade",
                )
                CircularProgressIndicator(
                    color = Accent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp).alpha(spinAlpha),
                )
            }
            return
        }

        if (installedApps.isEmpty()) {
            val epicLoggedIn by EpicAuthManager.isLoggedInFlow.collectAsState()
            val gogLoggedIn by GOGAuthManager.isLoggedInFlow.collectAsState()
            val anyLoggedIn = isLoggedIn || epicLoggedIn || gogLoggedIn
            val hasAnyCredentials =
                anyLoggedIn ||
                    SteamService.hasStoredCredentials(context) ||
                    EpicService.hasStoredCredentials(context) ||
                    GOGAuthManager.isLoggedIn(context)
            if (!anyLoggedIn && !hasAnyCredentials) {
                LoginRequiredScreen("Library") {
                    navigateToSettings(SettingsNavItem.STORES)
                }
            } else if (anyLoggedIn) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateMessage(stringResource(R.string.library_games_no_games_installed))
                }
            }
            return
        }

        var selectedAppForSettings by remember { mutableStateOf<SteamApp?>(null) }
        var selectedGogGameForSettings by remember { mutableStateOf<GOGGame?>(null) }
        var detailApp by remember { mutableStateOf<SteamApp?>(null) }
        var detailGogGame by remember { mutableStateOf<GOGGame?>(null) }
        val gridState = rememberLazyGridState()
        val carouselState = rememberLazyListState()
        val activity = LocalContext.current as? UnifiedActivity

        // Pause chasing borders on library cards while any dialog is open.
        LaunchedEffect(selectedAppForSettings, selectedGogGameForSettings, detailApp) {
            chasingBordersPaused.value =
                selectedAppForSettings != null || selectedGogGameForSettings != null || detailApp != null
        }
        DisposableEffect(Unit) {
            onDispose { chasingBordersPaused.value = false }
        }

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
        val focusRequesters =
            remember(displayedApps.size) {
                List(displayedApps.size) { FocusRequester() }
            }

        // Observe focus index changes from the activity and request focus on the target item
        val focusIndex by (activity?.libraryFocusIndex ?: kotlinx.coroutines.flow.MutableStateFlow(0)).collectAsState()
        LaunchedEffect(focusIndex, focusRequesters.size, layoutMode) {
            if (searchQuery.isEmpty() &&
                layoutMode == LibraryLayoutMode.GRID_4 &&
                focusRequesters.isNotEmpty() &&
                focusIndex in focusRequesters.indices
            ) {
                gridState.animateScrollToItem(focusIndex)
                try {
                    focusRequesters[focusIndex].requestFocus()
                } catch (_: Exception) {
                }
            }
        }

        // Track selected app for the top-right Game Settings button
        LaunchedEffect(focusIndex, displayedApps) {
            val app = displayedApps.getOrNull(focusIndex) ?: displayedApps.firstOrNull()
            selectedSteamAppId = app?.id ?: 0
            selectedSteamAppName = app?.name ?: ""
            val gogGame = app?.let { gogByPseudoId[it.id] }
            selectedLibrarySource =
                when {
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
            selectedLibrarySource =
                when {
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
                    modifier = Modifier.tabScreenPadding(),
                    gridState = gridState,
                    contentPadding = TabGridContentPadding,
                    clipContent = false,
                ) { app, index, rowHeight ->
                    GameCapsule(
                        app = app,
                        gogGame = gogByPseudoId[app.id],
                        iconRefreshKey = iconRefreshKey,
                        isFocusedOverride = index == focusIndex,
                        isControllerActive = ControllerHelper.isControllerConnected(),
                        shortcuts = cachedShortcuts,
                        onClick = {
                            detailGogGame = gogByPseudoId[app.id]
                            detailApp = app
                        },
                        onLongClick = {
                            openSettingsForApp(index, app)
                        },
                        modifier =
                            Modifier
                                .height(rowHeight)
                                .then(
                                    if (index in focusRequesters.indices) {
                                        Modifier.focusRequester(focusRequesters[index])
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                }
            }

            LibraryLayoutMode.CAROUSEL -> {
                CarouselView(
                    items = displayedApps,
                    modifier = Modifier.tabScreenPadding(top = TabCarouselTopPadding, bottom = TabCarouselBottomPadding),
                    listState = carouselState,
                    selectedIndex = focusIndex,
                    onCenteredIndexChanged = { centeredIndex ->
                        if (activity != null && activity.libraryFocusIndex.value != centeredIndex) {
                            activity.libraryFocusIndex.value = centeredIndex
                        }
                    },
                ) { app, index, isSelected, cardWidth, cardHeight ->
                    GameCapsule(
                        app = app,
                        gogGame = gogByPseudoId[app.id],
                        iconRefreshKey = iconRefreshKey,
                        isFocusedOverride = isSelected,
                        isControllerActive = ControllerHelper.isControllerConnected(),
                        shortcuts = cachedShortcuts,
                        onClick = {
                            detailGogGame = gogByPseudoId[app.id]
                            detailApp = app
                        },
                        onLongClick = { openSettingsForApp(index, app) },
                        useLibraryCapsule = true,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (index in focusRequesters.indices) {
                                        Modifier.focusRequester(focusRequesters[index])
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                }
                JoystickCarouselScroll(
                    listState = carouselState,
                    stickFlow = activity?.leftStickXState,
                    currentIndex = focusIndex,
                    itemCount = displayedApps.size,
                    onIndexChanged = { newIdx ->
                        activity?.libraryFocusIndex?.value = newIdx
                    },
                )
            }

            LibraryLayoutMode.LIST -> {
                val listViewState = rememberLazyListState()
                ListView(
                    items = displayedApps,
                    modifier = Modifier.tabScreenPadding(),
                    listState = listViewState,
                    contentPadding = TabListContentPadding,
                    selectedIndex = focusIndex,
                    onSelectedIndexChanged = { newIdx ->
                        activity?.libraryFocusIndex?.value = newIdx
                    },
                ) { app, index, isSelected ->
                    GameCapsule(
                        app = app,
                        gogGame = gogByPseudoId[app.id],
                        iconRefreshKey = iconRefreshKey,
                        isFocusedOverride = isSelected,
                        isControllerActive = ControllerHelper.isControllerConnected(),
                        shortcuts = cachedShortcuts,
                        onClick = {
                            detailGogGame = gogByPseudoId[app.id]
                            detailApp = app
                        },
                        onLongClick = { openSettingsForApp(index, app) },
                        listMode = true,
                        modifier =
                            Modifier
                                .then(
                                    if (index in focusRequesters.indices) {
                                        Modifier.focusRequester(focusRequesters[index])
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                }
                JoystickListScroll(
                    listState = listViewState,
                    stickFlow = activity?.rightStickScrollState,
                    minSpeed = 2.5f,
                    maxSpeed = 16f,
                    quadratic = true,
                )
            }
        }

        if (selectedAppForSettings != null) {
            GameSettingsDialog(
                app = selectedAppForSettings!!,
                onDismissRequest = { selectedAppForSettings = null },
            )
        }
        if (selectedGogGameForSettings != null) {
            GOGGameSettingsDialog(
                app = selectedGogGameForSettings!!,
                onDismissRequest = { selectedGogGameForSettings = null },
            )
        }
        if (detailApp != null) {
            LibraryGameDetailDialog(
                app = detailApp!!,
                gogGame = detailGogGame,
                onDismissRequest = {
                    detailApp = null
                    detailGogGame = null
                },
            )
        }
    }

    private enum class GameSettingsScreen {
        Menu,
        Shortcut,
        Saves,
        CloudSaves,
        Uninstall,
    }

    private data class HomeShortcutUiState(
        val shortcut: Shortcut? = null,
        val isPinned: Boolean = false,
    )

    private data class GameSettingsActionItem(
        val title: String,
        val icon: ImageVector,
        val accentColor: Color = Accent,
        val onClick: () -> Unit,
    )

    @Composable
    private fun GameSettingsDialogFrame(
        title: String,
        onDismissRequest: () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier =
                    Modifier
                        .widthIn(min = 200.dp, max = 280.dp),
                shape = RoundedCornerShape(14.dp),
                color = CardDark,
                border = BorderStroke(1.dp, CardBorder),
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    // Title header
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    content()
                }
            }
        }
    }

    @Composable
    private fun GameSettingsActionGrid(
        actions: List<GameSettingsActionItem>,
        modifier: Modifier = Modifier,
    ) {
        Column(modifier = modifier) {
            actions.forEachIndexed { index, action ->
                if (index > 0) {
                    HorizontalDivider(
                        color = CardBorder.copy(alpha = 0.5f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                GameSettingsActionCard(action = action)
            }
        }
    }

    @Composable
    private fun GameSettingsActionCard(
        action: GameSettingsActionItem,
        modifier: Modifier = Modifier,
    ) {
        val isDanger = action.accentColor == DangerRed
        val iconColor = if (isDanger) DangerRed else TextSecondary
        val textColor = if (isDanger) DangerRed else TextPrimary

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "actionCardScale",
        )
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = action.onClick,
                    ).padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun GameSettingsInfoCard(
        message: String,
        accentColor: Color = Accent,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }

    /**
     * Shared uninstall/remove confirmation UI used by GameSettingsDialog,
     * GOGGameSettingsDialog, and LibraryGameDetailDialog.
     */
    @Composable
    private fun UninstallConfirmation(
        message: String,
        confirmLabel: String = stringResource(R.string.common_ui_uninstall),
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        var isUninstalling by remember { mutableStateOf(false) }

        GameSettingsInfoCard(message = message, accentColor = DangerRed)

        if (isUninstalling) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = DangerRed)
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        isUninstalling = true
                        onConfirm()
                    },
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                ) {
                    Text(
                        confirmLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.common_ui_cancel), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @Composable
    private fun ShortcutRemovalConfirmation(
        message: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        var isRemoving by remember { mutableStateOf(false) }

        GameSettingsInfoCard(message = message, accentColor = DangerRed)

        if (isRemoving) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = DangerRed)
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        isRemoving = true
                        onConfirm()
                    },
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                ) {
                    Text(
                        stringResource(R.string.common_ui_remove),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.common_ui_cancel), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // Game Settings Dialog
    @Composable
    private fun GameSettingsDialog(
        app: SteamApp,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        var currentTab by remember { mutableStateOf(GameSettingsScreen.Menu) }
        val scope = rememberCoroutineScope()
        val isCustom = app.id < 0
        val isEpic = app.id >= 2000000000
        val epicId = if (isEpic) app.id - 2000000000 else 0
        var shortcutRefreshKey by remember(app.id, isCustom, isEpic, epicId) { mutableStateOf(0) }
        var pinnedShortcutOverride by remember(app.id, isCustom, isEpic, epicId) { mutableStateOf<Boolean?>(null) }
        val epicArtworkUrl by produceState<String?>(initialValue = null, key1 = isEpic, key2 = epicId) {
            value =
                if (isEpic) {
                    val epicGame = db.epicGameDao().getById(epicId)
                    epicGame?.primaryImageUrl ?: epicGame?.iconUrl
                } else {
                    null
                }
        }
        val currentRefreshSignal = this@UnifiedActivity.libraryRefreshSignal
        val homeShortcutState by produceState(
            HomeShortcutUiState(),
            app.id,
            isCustom,
            isEpic,
            epicId,
            currentRefreshSignal,
            shortcutRefreshKey,
        ) {
            value =
                withContext(Dispatchers.IO) {
                    val shortcut = findLibraryShortcutForGame(ContainerManager(context), app, isCustom, isEpic, epicId)
                    HomeShortcutUiState(
                        shortcut = shortcut,
                        isPinned = shortcut?.let { LibraryShortcutUtils.hasPinnedHomeShortcut(context, it) } == true,
                    )
                }
        }
        val hasPinnedShortcut = pinnedShortcutOverride ?: homeShortcutState.isPinned

        // Export logic
        val exportLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                if (uri != null) {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val os = context.contentResolver.openOutputStream(uri) ?: return@launch
                            val zos = java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(os))

                            val containerManager =
                                com.winlator.cmod.runtime.container
                                    .ContainerManager(context)
                            val shortcut = findLibraryShortcutForGame(containerManager, app, isCustom, isEpic, epicId)

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
                            fun zipDir(
                                dir: java.io.File,
                                baseName: String,
                            ) {
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
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    R.string.saves_import_export_exported,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    getString(R.string.saves_import_export_exported_failed, e.message),
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                        }
                    }
                }
            }

        // Import logic
        val importLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val `is` = context.contentResolver.openInputStream(uri) ?: return@launch
                            val zis = java.util.zip.ZipInputStream(java.io.BufferedInputStream(`is`))

                            val containerManager =
                                com.winlator.cmod.runtime.container
                                    .ContainerManager(context)
                            val shortcut = findLibraryShortcutForGame(containerManager, app, isCustom, isEpic, epicId)

                            val goldbergSavesParent =
                                java.io.File(
                                    if (isEpic) app.gameDir else SteamService.getAppDirPath(app.id),
                                    if (isEpic) "" else "steam_settings",
                                )
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
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    R.string.saves_import_export_imported,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    getString(R.string.saves_import_export_imported_failed, e.message),
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                        }
                    }
                }
            }

        GameSettingsDialogFrame(
            title = app.name,
            onDismissRequest = onDismissRequest,
        ) {
            when (currentTab) {
                GameSettingsScreen.Menu -> {
                    val actions =
                        listOf(
                            GameSettingsActionItem(
                                title = stringResource(R.string.common_ui_settings),
                                icon = Icons.Outlined.Settings,
                                onClick = {
                                    val containerManager = ContainerManager(context)
                                    val shortcut =
                                        findLibraryShortcutForGame(containerManager, app, isCustom, isEpic, epicId)
                                            ?: if (isCustom) {
                                                null
                                            } else {
                                                ShortcutSettingsComposeDialog.createLibraryShortcut(
                                                    context = context,
                                                    containerManager = containerManager,
                                                    source = if (isEpic) "EPIC" else "STEAM",
                                                    appId = if (isEpic) epicId else app.id,
                                                    gogId = null,
                                                    appName = app.name,
                                                )
                                            }
                                    if (shortcut != null) {
                                        ShortcutSettingsComposeDialog(this@UnifiedActivity, shortcut).show()
                                    }
                                    onDismissRequest()
                                },
                            ),
                            GameSettingsActionItem(
                                title =
                                    stringResource(
                                        if (hasPinnedShortcut) {
                                            R.string.common_ui_remove
                                        } else {
                                            R.string.common_ui_shortcut
                                        },
                                    ),
                                icon = Icons.Outlined.Home,
                                accentColor = if (hasPinnedShortcut) DangerRed else Accent,
                                onClick = {
                                    if (hasPinnedShortcut) {
                                        currentTab = GameSettingsScreen.Shortcut
                                    } else {
                                        scope.launch {
                                            val created =
                                                withContext(Dispatchers.IO) {
                                                    addLibraryShortcutToHomeScreen(
                                                        context,
                                                        app,
                                                        isCustom,
                                                        isEpic,
                                                        epicId,
                                                        epicArtworkUrl,
                                                    )
                                                }
                                            if (created) {
                                                pinnedShortcutOverride = true
                                                shortcutRefreshKey++
                                            }
                                            if (!created) {
                                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                                    context,
                                                    context.getString(
                                                        R.string.library_games_failed_to_create_shortcut,
                                                        app.name,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                },
                            ),
                            GameSettingsActionItem(
                                title = stringResource(R.string.saves_import_export_title),
                                icon = Icons.Outlined.Save,
                                onClick = { currentTab = GameSettingsScreen.Saves },
                            ),
                            GameSettingsActionItem(
                                title = stringResource(R.string.cloud_saves_title),
                                icon = Icons.Outlined.CloudSync,
                                onClick = { currentTab = GameSettingsScreen.CloudSaves },
                            ),
                            GameSettingsActionItem(
                                title =
                                    if (isCustom) {
                                        stringResource(
                                            R.string.common_ui_remove,
                                        )
                                    } else {
                                        stringResource(R.string.common_ui_uninstall)
                                    },
                                icon = Icons.Outlined.Delete,
                                accentColor = DangerRed,
                                onClick = { currentTab = GameSettingsScreen.Uninstall },
                            ),
                        )

                    GameSettingsActionGrid(actions = actions)
                }

                GameSettingsScreen.Shortcut -> {
                    ShortcutRemovalConfirmation(
                        message = stringResource(R.string.shortcuts_list_remove_game_shortcut_message, app.name),
                        onConfirm = {
                            scope.launch {
                                val removed =
                                    withContext(Dispatchers.IO) {
                                        homeShortcutState.shortcut?.let {
                                            LibraryShortcutUtils.disablePinnedHomeShortcut(context, it)
                                        } == true
                                    }
                                pinnedShortcutOverride = if (removed) false else hasPinnedShortcut
                                shortcutRefreshKey++
                                currentTab = GameSettingsScreen.Menu
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    if (removed) {
                                        context.getString(R.string.shortcuts_list_removed)
                                    } else {
                                        context.getString(R.string.common_ui_unknown_error)
                                    },
                                )
                            }
                        },
                        onCancel = { currentTab = GameSettingsScreen.Menu },
                    )
                }

                GameSettingsScreen.Saves -> {
                    GameSettingsActionGrid(
                        actions =
                            listOf(
                                GameSettingsActionItem(
                                    title = stringResource(R.string.common_ui_export),
                                    icon = Icons.Outlined.Upload,
                                    onClick = {
                                        exportLauncher.launch(
                                            "${app.name.replace(" ", "_").replace(":", "")}_Saves.zip",
                                        )
                                    },
                                ),
                                GameSettingsActionItem(
                                    title = stringResource(R.string.common_ui_import),
                                    icon = Icons.Outlined.Download,
                                    onClick = { importLauncher.launch(arrayOf("application/zip")) },
                                ),
                                GameSettingsActionItem(
                                    title = stringResource(R.string.common_ui_back),
                                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                    onClick = { currentTab = GameSettingsScreen.Menu },
                                ),
                            ),
                    )
                }

                GameSettingsScreen.CloudSaves -> {
                    var isWorking by remember { mutableStateOf(false) }
                    val shortcut =
                        remember(app.id, epicId, isCustom, isEpic) {
                            findLibraryShortcutForGame(ContainerManager(context), app, isCustom, isEpic, epicId)
                        }
                    var cloudSyncEnabled by remember(shortcut?.file?.absolutePath) {
                        mutableStateOf(isShortcutCloudSyncEnabled(shortcut))
                    }

                    val gameSource =
                        when {
                            isEpic -> GameSaveBackupManager.GameSource.EPIC
                            else -> GameSaveBackupManager.GameSource.STEAM
                        }
                    val gameIdStr = if (isEpic) epicId.toString() else app.id.toString()

                    CloudSavesContent(
                        isWorking = isWorking,
                        cloudSyncEnabled = cloudSyncEnabled,
                        onCloudSyncToggle = { enabled ->
                            cloudSyncEnabled = enabled
                            setShortcutCloudSyncEnabled(shortcut, enabled)
                            com.winlator.cmod.shared.android.AppUtils.showToast(
                                context,
                                if (enabled) {
                                    context.getString(R.string.cloud_sync_enabled_summary)
                                } else {
                                    context.getString(R.string.cloud_sync_disabled_summary)
                                },
                                android.widget.Toast.LENGTH_SHORT,
                            )
                        },
                        onBackup = {
                            if (!isWorking) {
                                isWorking = true
                                scope.launch {
                                    val result =
                                        GameSaveBackupManager.backupToGoogle(
                                            this@UnifiedActivity,
                                            gameSource,
                                            gameIdStr,
                                            app.name,
                                        )
                                    isWorking = false
                                    com.winlator.cmod.shared.android.AppUtils.showToast(
                                        context,
                                        result.message,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                }
                            }
                        },
                        onRestore = {
                            if (!isWorking) {
                                isWorking = true
                                scope.launch {
                                    val result =
                                        GameSaveBackupManager.restoreFromGoogle(
                                            this@UnifiedActivity,
                                            gameSource,
                                            gameIdStr,
                                            app.name,
                                        )
                                    isWorking = false
                                    com.winlator.cmod.shared.android.AppUtils.showToast(
                                        context,
                                        result.message,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                }
                            }
                        },
                        onBack = { currentTab = GameSettingsScreen.Menu },
                    )
                }

                GameSettingsScreen.Uninstall -> {
                    UninstallConfirmation(
                        message =
                            if (isCustom) {
                                getString(R.string.library_games_remove_confirm, app.name)
                            } else {
                                getString(R.string.library_games_uninstall_confirm, app.name)
                            },
                        confirmLabel =
                            if (isCustom) {
                                stringResource(
                                    R.string.common_ui_remove,
                                )
                            } else {
                                stringResource(R.string.common_ui_uninstall)
                            },
                        onConfirm = {
                            if (isCustom) {
                                scope.launch(Dispatchers.IO) {
                                    val cm = ContainerManager(context)
                                    val sc = findLibraryShortcutForGame(cm, app, isCustom, isEpic, epicId)
                                    sc?.let { LibraryShortcutUtils.deleteShortcutArtifacts(context, it) }
                                    java.io.File(context.filesDir, "custom_icons/${app.name.replace("/", "_")}.png").delete()
                                    PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(app.id))
                                    withContext(Dispatchers.Main) {
                                        com.winlator.cmod.shared.android.AppUtils.showToast(
                                            context,
                                            getString(R.string.library_games_game_removed, app.name),
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                        onDismissRequest()
                                    }
                                }
                            } else if (isEpic) {
                                scope.launch(Dispatchers.IO) {
                                    val result = EpicService.deleteGame(context, epicId)
                                    withContext(Dispatchers.Main) {
                                        if (result.isSuccess) {
                                            com.winlator.cmod.shared.android.AppUtils.showToast(
                                                context,
                                                getString(R.string.library_games_game_uninstalled, app.name),
                                                android.widget.Toast.LENGTH_SHORT,
                                            )
                                        } else {
                                            com.winlator.cmod.shared.android.AppUtils.showToast(
                                                context,
                                                getString(
                                                    R.string.library_games_failed_to_uninstall_reason,
                                                    result.exceptionOrNull()?.message ?: "Unknown error",
                                                ),
                                                android.widget.Toast.LENGTH_LONG,
                                            )
                                        }
                                        onDismissRequest()
                                    }
                                }
                            } else {
                                SteamService.uninstallApp(app.id) { success ->
                                    if (success) {
                                        com.winlator.cmod.shared.android.AppUtils.showToast(
                                            context,
                                            getString(R.string.library_games_game_uninstalled, app.name),
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                    } else {
                                        com.winlator.cmod.shared.android.AppUtils.showToast(
                                            context,
                                            getString(R.string.library_games_failed_to_uninstall),
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                    }
                                    onDismissRequest()
                                }
                            }
                        },
                        onCancel = { currentTab = GameSettingsScreen.Menu },
                    )
                }
            }
        }
    }

    @Composable
    private fun GOGGameSettingsDialog(
        app: GOGGame,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        var currentTab by remember { mutableStateOf(GameSettingsScreen.Menu) }
        val scope = rememberCoroutineScope()
        var shortcutRefreshKey by remember(app.id) { mutableStateOf(0) }
        var pinnedShortcutOverride by remember(app.id) { mutableStateOf<Boolean?>(null) }
        val currentRefreshSignal = this@UnifiedActivity.libraryRefreshSignal
        val homeShortcutState by produceState(
            HomeShortcutUiState(),
            app.id,
            currentRefreshSignal,
            shortcutRefreshKey,
        ) {
            value =
                withContext(Dispatchers.IO) {
                    val shortcut =
                        ContainerManager(context).loadShortcuts().find {
                            it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == app.id
                        }
                    HomeShortcutUiState(
                        shortcut = shortcut,
                        isPinned = shortcut?.let { LibraryShortcutUtils.hasPinnedHomeShortcut(context, it) } == true,
                    )
                }
        }
        val hasPinnedShortcut = pinnedShortcutOverride ?: homeShortcutState.isPinned

        GameSettingsDialogFrame(
            title = app.title,
            onDismissRequest = onDismissRequest,
        ) {
            when (currentTab) {
                GameSettingsScreen.Menu -> {
                    GameSettingsActionGrid(
                        actions =
                            listOf(
                                GameSettingsActionItem(
                                    title = stringResource(R.string.common_ui_settings),
                                    icon = Icons.Outlined.Settings,
                                    onClick = {
                                        val containerManager = ContainerManager(context)
                                        val shortcut =
                                            containerManager.loadShortcuts().find {
                                                it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == app.id
                                            } ?: ShortcutSettingsComposeDialog.createLibraryShortcut(
                                                context = context,
                                                containerManager = containerManager,
                                                source = "GOG",
                                                appId = gogPseudoId(app.id),
                                                gogId = app.id,
                                                appName = app.title,
                                            )
                                        if (shortcut != null) {
                                            ShortcutSettingsComposeDialog(this@UnifiedActivity, shortcut).show()
                                        }
                                        onDismissRequest()
                                    },
                                ),
                                GameSettingsActionItem(
                                    title =
                                        stringResource(
                                            if (hasPinnedShortcut) {
                                                R.string.common_ui_remove
                                            } else {
                                                R.string.common_ui_shortcut
                                            },
                                        ),
                                    icon = Icons.Outlined.Home,
                                    accentColor = if (hasPinnedShortcut) DangerRed else Accent,
                                    onClick = {
                                        if (hasPinnedShortcut) {
                                            currentTab = GameSettingsScreen.Shortcut
                                        } else {
                                            scope.launch {
                                                val artworkUrl = app.imageUrl.ifEmpty { app.iconUrl }
                                                val created =
                                                    withContext(Dispatchers.IO) {
                                                        addGogShortcutToHomeScreen(context, app, artworkUrl)
                                                    }
                                                if (created) {
                                                    pinnedShortcutOverride = true
                                                    shortcutRefreshKey++
                                                }
                                                if (!created) {
                                                    com.winlator.cmod.shared.android.AppUtils.showToast(
                                                        context,
                                                        context.getString(
                                                            R.string.library_games_failed_to_create_shortcut,
                                                            app.title,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    },
                                ),
                                GameSettingsActionItem(
                                    title = stringResource(R.string.saves_import_export_title),
                                    icon = Icons.Outlined.Save,
                                    onClick = { currentTab = GameSettingsScreen.Saves },
                                ),
                                GameSettingsActionItem(
                                    title = stringResource(R.string.cloud_saves_title),
                                    icon = Icons.Outlined.CloudSync,
                                    onClick = { currentTab = GameSettingsScreen.CloudSaves },
                                ),
                                GameSettingsActionItem(
                                    title = stringResource(R.string.common_ui_uninstall),
                                    icon = Icons.Outlined.Delete,
                                    accentColor = DangerRed,
                                    onClick = { currentTab = GameSettingsScreen.Uninstall },
                                ),
                            ),
                    )
                }

                GameSettingsScreen.Shortcut -> {
                    ShortcutRemovalConfirmation(
                        message = stringResource(R.string.shortcuts_list_remove_game_shortcut_message, app.title),
                        onConfirm = {
                            scope.launch {
                                val removed =
                                    withContext(Dispatchers.IO) {
                                        homeShortcutState.shortcut?.let {
                                            LibraryShortcutUtils.disablePinnedHomeShortcut(context, it)
                                        } == true
                                    }
                                pinnedShortcutOverride = if (removed) false else hasPinnedShortcut
                                shortcutRefreshKey++
                                currentTab = GameSettingsScreen.Menu
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    if (removed) {
                                        context.getString(R.string.shortcuts_list_removed)
                                    } else {
                                        context.getString(R.string.common_ui_unknown_error)
                                    },
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                        },
                        onCancel = { currentTab = GameSettingsScreen.Menu },
                    )
                }

                GameSettingsScreen.Saves -> {
                    GameSettingsActionGrid(
                        actions =
                            listOf(
                                GameSettingsActionItem(
                                    title = stringResource(R.string.common_ui_sync),
                                    icon = Icons.Outlined.Cloud,
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            GOGService.syncCloudSaves(context, "GOG_${app.id}", "auto")
                                        }
                                        com.winlator.cmod.shared.android.AppUtils.showToast(
                                            context,
                                            getString(R.string.google_cloud_sync_started),
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                    },
                                ),
                                GameSettingsActionItem(
                                    title = stringResource(R.string.common_ui_back),
                                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                    onClick = { currentTab = GameSettingsScreen.Menu },
                                ),
                            ),
                    )
                }

                GameSettingsScreen.CloudSaves -> {
                    var isWorking by remember { mutableStateOf(false) }
                    val shortcut =
                        remember(app.id) {
                            ContainerManager(context).loadShortcuts().find {
                                it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == app.id
                            }
                        }
                    var cloudSyncEnabled by remember(shortcut?.file?.absolutePath) {
                        mutableStateOf(isShortcutCloudSyncEnabled(shortcut))
                    }

                    CloudSavesContent(
                        isWorking = isWorking,
                        cloudSyncEnabled = cloudSyncEnabled,
                        onCloudSyncToggle = { enabled ->
                            cloudSyncEnabled = enabled
                            setShortcutCloudSyncEnabled(shortcut, enabled)
                            com.winlator.cmod.shared.android.AppUtils.showToast(
                                context,
                                if (enabled) {
                                    context.getString(R.string.cloud_sync_enabled_summary)
                                } else {
                                    context.getString(R.string.cloud_sync_disabled_summary)
                                },
                                android.widget.Toast.LENGTH_SHORT,
                            )
                        },
                        onBackup = {
                            if (!isWorking) {
                                isWorking = true
                                scope.launch {
                                    val result =
                                        GameSaveBackupManager.backupToGoogle(
                                            this@UnifiedActivity,
                                            GameSaveBackupManager.GameSource.GOG,
                                            app.id,
                                            app.title,
                                        )
                                    isWorking = false
                                    com.winlator.cmod.shared.android.AppUtils.showToast(
                                        context,
                                        result.message,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                }
                            }
                        },
                        onRestore = {
                            if (!isWorking) {
                                isWorking = true
                                scope.launch {
                                    val result =
                                        GameSaveBackupManager.restoreFromGoogle(
                                            this@UnifiedActivity,
                                            GameSaveBackupManager.GameSource.GOG,
                                            app.id,
                                            app.title,
                                        )
                                    isWorking = false
                                    com.winlator.cmod.shared.android.AppUtils.showToast(
                                        context,
                                        result.message,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                }
                            }
                        },
                        onBack = { currentTab = GameSettingsScreen.Menu },
                    )
                }

                GameSettingsScreen.Uninstall -> {
                    UninstallConfirmation(
                        message = getString(R.string.library_games_uninstall_confirm, app.title),
                        onConfirm = {
                            scope.launch(Dispatchers.IO) {
                                GOGService.deleteGame(
                                    context,
                                    LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.feature.stores.steam.enums.GameSource.GOG),
                                )
                                withContext(Dispatchers.Main) {
                                    com.winlator.cmod.shared.android.AppUtils.showToast(
                                        context,
                                        getString(R.string.library_games_game_uninstalled, app.title),
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                    onDismissRequest()
                                }
                            }
                        },
                        onCancel = { currentTab = GameSettingsScreen.Menu },
                    )
                }
            }
        }
    }

    // Library Game Detail Dialog

    private enum class LibraryDetailScreen { Main, Shortcut, Saves, CloudSaves, Uninstall }

    @Composable
    private fun LibraryGameDetailDialog(
        app: SteamApp,
        gogGame: GOGGame? = null,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var currentScreen by remember { mutableStateOf(LibraryDetailScreen.Main) }
        var shortcutRefreshKey by remember(app.id, gogGame?.id) { mutableStateOf(0) }
        var pinnedShortcutOverride by remember(app.id, gogGame?.id) { mutableStateOf<Boolean?>(null) }

        val isCustom = app.id < 0
        val isEpic = app.id >= 2000000000
        val isGog = gogGame != null
        val epicId = if (isEpic) app.id - 2000000000 else 0

        val epicGame by produceState<EpicGame?>(initialValue = null, key1 = epicId) {
            value = if (isEpic) db.epicGameDao().getById(epicId) else null
        }

        val epicArtworkUrl by produceState<String?>(initialValue = null, key1 = isEpic, key2 = epicId) {
            value =
                if (isEpic) {
                    val eg = db.epicGameDao().getById(epicId)
                    eg?.primaryImageUrl ?: eg?.iconUrl
                } else {
                    null
                }
        }
        val currentRefreshSignal = this@UnifiedActivity.libraryRefreshSignal
        val homeShortcutState by produceState(
            HomeShortcutUiState(),
            app.id,
            gogGame?.id,
            isCustom,
            isEpic,
            isGog,
            epicId,
            currentRefreshSignal,
            shortcutRefreshKey,
        ) {
            value =
                withContext(Dispatchers.IO) {
                    val shortcut =
                        when {
                            isGog -> {
                                ContainerManager(context).loadShortcuts().find {
                                    it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == gogGame!!.id
                                }
                            }

                            else -> {
                                findLibraryShortcutForGame(ContainerManager(context), app, isCustom, isEpic, epicId)
                            }
                        }
                    HomeShortcutUiState(
                        shortcut = shortcut,
                        isPinned = shortcut?.let { LibraryShortcutUtils.hasPinnedHomeShortcut(context, it) } == true,
                    )
                }
        }
        val hasPinnedShortcut = pinnedShortcutOverride ?: homeShortcutState.isPinned

        // Hero image
        val heroImageUrl: Any? =
            when {
                isGog -> {
                    gogGame!!.imageUrl.ifEmpty { gogGame.iconUrl }
                }

                isEpic -> {
                    epicGame?.primaryImageUrl ?: epicGame?.iconUrl
                }

                isCustom -> {
                    val safeName = app.name.replace("/", "_").replace("\\", "_")
                    val iconFile = java.io.File(context.filesDir, "custom_icons/$safeName.png")
                    if (iconFile.exists()) iconFile else null
                }

                else -> {
                    app.getHeroUrl()
                }
            }

        val subtitle =
            when {
                isGog -> {
                    gogGame!!.developer
                }

                isCustom -> {
                    stringResource(R.string.library_games_custom_game)
                }

                isEpic -> {
                    epicGame?.developer ?: ""
                }

                else -> {
                    listOfNotNull(
                        app.developer.takeIf { it.isNotBlank() },
                        app.publisher.takeIf { it.isNotBlank() },
                    ).joinToString(" • ")
                }
            }

        // Playtime info
        val playtimePrefs =
            remember {
                context.getSharedPreferences("playtime_stats", android.content.Context.MODE_PRIVATE)
            }
        val searchKey =
            remember(app) {
                if (app.id >= 2000000000 || app.id < 0) {
                    app.name
                } else {
                    app.name.replace(LIBRARY_NAME_SANITIZE_REGEX, "")
                }
            }
        val lastPlayed = playtimePrefs.getLong("${searchKey}_last_played", 0L)
        val totalPlaytime = playtimePrefs.getLong("${searchKey}_playtime", 0L)
        val playCount = playtimePrefs.getInt("${searchKey}_play_count", 0)

        val sourceLabel =
            when {
                isGog -> "GOG"
                isEpic -> "Epic Games"
                isCustom -> "Custom"
                else -> "Steam"
            }

        // Install path
        val installPath =
            remember(app, gogGame) {
                when {
                    isGog -> {
                        gogGame!!.installPath
                    }

                    isEpic -> {
                        epicGame?.installPath ?: ""
                    }

                    isCustom -> {
                        app.gameDir
                    }

                    else -> {
                        try {
                            SteamService.getAppDirPath(app.id)
                        } catch (_: Exception) {
                            ""
                        }
                    }
                }
            }

        // Install size (computed async)
        val installSizeText by produceState<String?>(initialValue = null, key1 = installPath) {
            value =
                if (installPath.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val bytes = StorageUtils.getFolderSize(installPath)
                            if (bytes > 0) StorageUtils.formatBinarySize(bytes) else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else {
                    null
                }
        }

        // Export / Import launchers (reuse GameSettingsDialog pattern)

        val exportLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                if (uri != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val os = context.contentResolver.openOutputStream(uri) ?: return@launch
                            val zos = java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(os))
                            val containerManager = ContainerManager(context)
                            val shortcut = findLibraryShortcutForGame(containerManager, app, isCustom, isEpic, epicId)
                            val dirsToZip = mutableListOf<java.io.File>()
                            val goldbergSaves = java.io.File(SteamService.getAppDirPath(app.id), "steam_settings/saves")
                            if (goldbergSaves.exists() && goldbergSaves.isDirectory) dirsToZip.add(goldbergSaves)
                            if (shortcut != null) {
                                val prefixDir = java.io.File(shortcut.container.getRootDir(), ".wine/drive_c/users/xuser")
                                listOf("Documents", "Saved Games", "AppData").forEach { name ->
                                    val dir = java.io.File(prefixDir, name)
                                    if (dir.exists()) dirsToZip.add(dir)
                                }
                            }

                            fun zipDir(
                                dir: java.io.File,
                                baseName: String,
                            ) {
                                val children = dir.listFiles() ?: return
                                for (child in children) {
                                    val name = if (baseName.isEmpty()) child.name else "$baseName/${child.name}"
                                    if (child.isDirectory) {
                                        zos.putNextEntry(java.util.zip.ZipEntry("$name/"))
                                        zos.closeEntry()
                                        zipDir(child, name)
                                    } else {
                                        zos.putNextEntry(java.util.zip.ZipEntry(name))
                                        child.inputStream().use { it.copyTo(zos) }
                                        zos.closeEntry()
                                    }
                                }
                            }
                            for (dir in dirsToZip) {
                                zos.putNextEntry(java.util.zip.ZipEntry("${dir.name}/"))
                                zos.closeEntry()
                                zipDir(dir, dir.name)
                            }
                            zos.close()
                            withContext(Dispatchers.Main) {
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    R.string.saves_import_export_exported,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    getString(R.string.saves_import_export_exported_failed, e.message),
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                        }
                    }
                }
            }

        val importLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                            val zis = java.util.zip.ZipInputStream(java.io.BufferedInputStream(inputStream))
                            val containerManager = ContainerManager(context)
                            val shortcut = findLibraryShortcutForGame(containerManager, app, isCustom, isEpic, epicId)
                            val goldbergSavesParent =
                                java.io.File(
                                    if (isEpic) app.gameDir else SteamService.getAppDirPath(app.id),
                                    if (isEpic) "" else "steam_settings",
                                )
                            val prefixDir = shortcut?.let { java.io.File(it.container.getRootDir(), ".wine/drive_c/users/xuser") }
                            var ze: java.util.zip.ZipEntry?
                            while (zis.nextEntry.also { ze = it } != null) {
                                val entry = ze!!
                                val name = entry.name
                                var destFile: java.io.File? = null
                                if (name.startsWith("saves/")) {
                                    destFile = java.io.File(goldbergSavesParent, name)
                                } else if (prefixDir != null &&
                                    (name.startsWith("Documents/") || name.startsWith("Saved Games/") || name.startsWith("AppData/"))
                                ) {
                                    destFile = java.io.File(prefixDir, name)
                                }
                                if (destFile != null) {
                                    if (entry.isDirectory) {
                                        destFile.mkdirs()
                                    } else {
                                        destFile.parentFile?.mkdirs()
                                        java.io.FileOutputStream(destFile).use { fos -> zis.copyTo(fos) }
                                    }
                                }
                                zis.closeEntry()
                            }
                            zis.close()
                            withContext(Dispatchers.Main) {
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    R.string.saves_import_export_imported,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                    context,
                                    getString(R.string.saves_import_export_imported_failed, e.message),
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                        }
                    }
                }
            }

        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.864f).fillMaxHeight(0.96f),
                shape = RoundedCornerShape(20.dp),
                color = CardDark,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        // Hero image section
                        Box(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.38f),
                        ) {
                            if (heroImageUrl != null) {
                                AsyncImage(
                                    model =
                                        ImageRequest
                                            .Builder(context)
                                            .data(heroImageUrl)
                                            .crossfade(150)
                                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .build(),
                                    contentDescription = "${app.name} artwork",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.FillWidth,
                                    alignment = Alignment.TopCenter,
                                )
                            } else {
                                Box(
                                    Modifier.fillMaxSize().background(SurfaceDark),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.SportsEsports,
                                        contentDescription = null,
                                        tint = Accent.copy(alpha = 0.4f),
                                        modifier = Modifier.size(72.dp),
                                    )
                                }
                            }
                            Box(
                                modifier =
                                    Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0.0f to Color.Transparent,
                                                    0.45f to Color.Transparent,
                                                    0.72f to CardDark.copy(alpha = 0.72f),
                                                    1.0f to CardDark,
                                                ),
                                        ),
                                    ),
                            )
                            Column(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 24.dp, end = 80.dp, bottom = 36.dp),
                            ) {
                                Text(
                                    app.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (subtitle.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        // Bottom content
                        when (currentScreen) {
                            LibraryDetailScreen.Main -> {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    // Left: Game details as individual cards
                                    Column(
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                                    ) {
                                        // Source badge row
                                        Surface(
                                            color = Accent.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Text(
                                                sourceLabel,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                color = Accent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }

                                        if (installPath.isNotBlank() || installSizeText != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                if (installPath.isNotBlank()) {
                                                    DetailCard(
                                                        label = stringResource(R.string.library_games_install_path),
                                                        value = installPath,
                                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                                    )
                                                }
                                                if (installSizeText != null) {
                                                    DetailCard(
                                                        stringResource(R.string.common_ui_size),
                                                        installSizeText!!,
                                                        modifier = Modifier.fillMaxHeight(),
                                                    )
                                                }
                                            }
                                        }

                                        // Release date card
                                        if (app.releaseDate > 0L) {
                                            val releaseDateText =
                                                remember(app.releaseDate) {
                                                    java.text
                                                        .SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                                                        .format(java.util.Date(app.releaseDate * 1000L))
                                                }
                                            DetailCard(stringResource(R.string.common_ui_release_date), releaseDateText)
                                        }
                                    }

                                    // Right: Compact action buttons
                                    Column(
                                        modifier =
                                            Modifier
                                                .widthIn(min = 200.dp, max = 260.dp)
                                                .fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                                    ) {
                                        // Play button — animated gradient
                                        PlayButton(onClick = {
                                            val containerManager = ContainerManager(context)
                                            if (isCustom) {
                                                launchCustomGame(context, containerManager, app.name)
                                            } else if (isGog) {
                                                launchGogGame(context, containerManager, gogGame!!)
                                            } else if (isEpic) {
                                                epicGame?.let { launchEpicGame(context, containerManager, it) }
                                            } else {
                                                launchSteamGame(context, containerManager, app)
                                            }
                                            onDismissRequest()
                                        })

                                        // Settings + Shortcut — half width each
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            CompactActionButton(
                                                icon = Icons.Outlined.Settings,
                                                label = stringResource(R.string.common_ui_settings),
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    val containerManager = ContainerManager(context)
                                                    val shortcut: com.winlator.cmod.runtime.container.Shortcut? =
                                                        when {
                                                            isGog -> {
                                                                containerManager.loadShortcuts().find {
                                                                    it.getExtra("game_source") == "GOG" &&
                                                                        it.getExtra("gog_id") == gogGame!!.id
                                                                } ?: ShortcutSettingsComposeDialog.createLibraryShortcut(
                                                                    context = context,
                                                                    containerManager = containerManager,
                                                                    source = "GOG",
                                                                    appId = gogPseudoId(gogGame!!.id),
                                                                    gogId = gogGame.id,
                                                                    appName = app.name,
                                                                )
                                                            }

                                                            isCustom -> {
                                                                findLibraryShortcutForGame(containerManager, app, isCustom, isEpic, epicId)
                                                            }

                                                            else -> {
                                                                findLibraryShortcutForGame(containerManager, app, isCustom, isEpic, epicId)
                                                                    ?: ShortcutSettingsComposeDialog.createLibraryShortcut(
                                                                        context = context,
                                                                        containerManager = containerManager,
                                                                        source = if (isEpic) "EPIC" else "STEAM",
                                                                        appId = if (isEpic) epicId else app.id,
                                                                        gogId = null,
                                                                        appName = app.name,
                                                                    )
                                                            }
                                                        }
                                                    if (shortcut != null) {
                                                        // Layer the settings dialog on top; keep the detail dialog open underneath.
                                                        ShortcutSettingsComposeDialog(this@UnifiedActivity, shortcut).show()
                                                    }
                                                },
                                            )

                                            CompactActionButton(
                                                icon = Icons.Outlined.Home,
                                                label =
                                                    stringResource(
                                                        if (hasPinnedShortcut) {
                                                            R.string.common_ui_remove
                                                        } else {
                                                            R.string.common_ui_shortcut
                                                        },
                                                    ),
                                                tint = if (hasPinnedShortcut) DangerRed else TextPrimary,
                                                bgColor = if (hasPinnedShortcut) DangerRed.copy(alpha = 0.12f) else SurfaceDark,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    if (hasPinnedShortcut) {
                                                        currentScreen = LibraryDetailScreen.Shortcut
                                                    } else {
                                                        scope.launch {
                                                            val created =
                                                                withContext(Dispatchers.IO) {
                                                                    if (isGog) {
                                                                        val artworkUrl = gogGame!!.imageUrl.ifEmpty { gogGame.iconUrl }
                                                                        addGogShortcutToHomeScreen(context, gogGame, artworkUrl)
                                                                    } else {
                                                                        addLibraryShortcutToHomeScreen(
                                                                            context,
                                                                            app,
                                                                            isCustom,
                                                                            isEpic,
                                                                            epicId,
                                                                            epicArtworkUrl,
                                                                        )
                                                                    }
                                                                }
                                                            if (created) {
                                                                pinnedShortcutOverride = true
                                                                shortcutRefreshKey++
                                                            }
                                                            if (!created) {
                                                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                                                    context,
                                                                    context.getString(
                                                                        R.string.library_games_failed_to_create_shortcut,
                                                                        app.name,
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            CompactActionButton(
                                                icon = Icons.Outlined.Save,
                                                label = stringResource(R.string.saves_import_export_title),
                                                modifier = Modifier.weight(1f),
                                                onClick = { currentScreen = LibraryDetailScreen.Saves },
                                            )

                                            CompactActionButton(
                                                icon = Icons.Outlined.CloudSync,
                                                label = stringResource(R.string.cloud_saves_title),
                                                modifier = Modifier.weight(1f),
                                                onClick = { currentScreen = LibraryDetailScreen.CloudSaves },
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            CompactActionButton(
                                                icon = Icons.Outlined.Delete,
                                                label =
                                                    if (isCustom) {
                                                        stringResource(
                                                            R.string.common_ui_remove,
                                                        )
                                                    } else {
                                                        stringResource(R.string.common_ui_uninstall)
                                                    },
                                                tint = DangerRed,
                                                bgColor = DangerRed.copy(alpha = 0.12f),
                                                modifier = Modifier.weight(1f),
                                                onClick = { currentScreen = LibraryDetailScreen.Uninstall },
                                            )
                                        }
                                    }
                                }
                            }

                            LibraryDetailScreen.Shortcut -> {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp, vertical = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.common_ui_shortcut),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.1.sp,
                                    )

                                    Spacer(Modifier.weight(1f))

                                    ShortcutRemovalConfirmation(
                                        message =
                                            stringResource(
                                                R.string.shortcuts_list_remove_game_shortcut_message,
                                                if (isGog) gogGame!!.title else app.name,
                                            ),
                                        onConfirm = {
                                            scope.launch {
                                                val removed =
                                                    withContext(Dispatchers.IO) {
                                                        homeShortcutState.shortcut?.let {
                                                            LibraryShortcutUtils.disablePinnedHomeShortcut(context, it)
                                                        } == true
                                                    }
                                                pinnedShortcutOverride = if (removed) false else hasPinnedShortcut
                                                shortcutRefreshKey++
                                                currentScreen = LibraryDetailScreen.Main
                                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                                    context,
                                                    if (removed) {
                                                        context.getString(R.string.shortcuts_list_removed)
                                                    } else {
                                                        context.getString(R.string.common_ui_unknown_error)
                                                    },
                                                )
                                            }
                                        },
                                        onCancel = { currentScreen = LibraryDetailScreen.Main },
                                    )
                                }
                            }

                            LibraryDetailScreen.Saves -> {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp, vertical = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.library_games_save_management),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.1.sp,
                                    )

                                    if (isGog) {
                                        GameSettingsActionGrid(
                                            actions =
                                                listOf(
                                                    GameSettingsActionItem(
                                                        title = stringResource(R.string.common_ui_sync),
                                                        icon = Icons.Outlined.Cloud,
                                                        onClick = {
                                                            scope.launch(Dispatchers.IO) {
                                                                GOGService.syncCloudSaves(context, "GOG_${gogGame!!.id}", "auto")
                                                            }
                                                            com.winlator.cmod.shared.android.AppUtils.showToast(
                                                                context,
                                                                getString(R.string.google_cloud_sync_started),
                                                                android.widget.Toast.LENGTH_SHORT,
                                                            )
                                                        },
                                                    ),
                                                    GameSettingsActionItem(
                                                        title = stringResource(R.string.common_ui_export),
                                                        icon = Icons.Outlined.Upload,
                                                        onClick = {
                                                            exportLauncher.launch(
                                                                "${app.name.replace(" ", "_").replace(":", "")}_Saves.zip",
                                                            )
                                                        },
                                                    ),
                                                    GameSettingsActionItem(
                                                        title = stringResource(R.string.common_ui_import),
                                                        icon = Icons.Outlined.Download,
                                                        onClick = { importLauncher.launch(arrayOf("application/zip")) },
                                                    ),
                                                ),
                                        )
                                    } else {
                                        GameSettingsActionGrid(
                                            actions =
                                                listOf(
                                                    GameSettingsActionItem(
                                                        title = stringResource(R.string.common_ui_export),
                                                        icon = Icons.Outlined.Upload,
                                                        onClick = {
                                                            exportLauncher.launch(
                                                                "${app.name.replace(" ", "_").replace(":", "")}_Saves.zip",
                                                            )
                                                        },
                                                    ),
                                                    GameSettingsActionItem(
                                                        title = stringResource(R.string.common_ui_import),
                                                        icon = Icons.Outlined.Download,
                                                        onClick = { importLauncher.launch(arrayOf("application/zip")) },
                                                    ),
                                                ),
                                        )
                                    }

                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { currentScreen = LibraryDetailScreen.Main }) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.ArrowBack,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.common_ui_back), color = TextSecondary)
                                    }
                                }
                            }

                            LibraryDetailScreen.CloudSaves -> {
                                var isWorking by remember { mutableStateOf(false) }

                                val detailGameSource =
                                    when {
                                        isGog -> GameSaveBackupManager.GameSource.GOG
                                        isEpic -> GameSaveBackupManager.GameSource.EPIC
                                        else -> GameSaveBackupManager.GameSource.STEAM
                                    }
                                val detailGameId =
                                    when {
                                        isGog -> gogGame!!.id
                                        isEpic -> epicId.toString()
                                        else -> app.id.toString()
                                    }
                                val detailShortcut =
                                    remember(app.id, gogGame?.id, epicId, isGog, isEpic, isCustom) {
                                        val containerManager = ContainerManager(context)
                                        when {
                                            isGog -> {
                                                containerManager.loadShortcuts().find {
                                                    it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == gogGame!!.id
                                                }
                                            }

                                            else -> {
                                                findLibraryShortcutForGame(containerManager, app, isCustom, isEpic, epicId)
                                            }
                                        }
                                    }
                                var cloudSyncEnabled by remember(detailShortcut?.file?.absolutePath) {
                                    mutableStateOf(isShortcutCloudSyncEnabled(detailShortcut))
                                }

                                CloudSavesContent(
                                    isWorking = isWorking,
                                    cloudSyncEnabled = cloudSyncEnabled,
                                    onCloudSyncToggle = { enabled ->
                                        cloudSyncEnabled = enabled
                                        setShortcutCloudSyncEnabled(detailShortcut, enabled)
                                        com.winlator.cmod.shared.android.AppUtils.showToast(
                                            context,
                                            if (enabled) {
                                                context.getString(R.string.cloud_sync_enabled_summary)
                                            } else {
                                                context.getString(R.string.cloud_sync_disabled_summary)
                                            },
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                    },
                                    onBackup = {
                                        if (!isWorking) {
                                            isWorking = true
                                            scope.launch {
                                                val result =
                                                    GameSaveBackupManager.backupToGoogle(
                                                        this@UnifiedActivity,
                                                        detailGameSource,
                                                        detailGameId,
                                                        app.name,
                                                    )
                                                isWorking = false
                                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                                    context,
                                                    result.message,
                                                    android.widget.Toast.LENGTH_SHORT,
                                                )
                                            }
                                        }
                                    },
                                    onRestore = {
                                        if (!isWorking) {
                                            isWorking = true
                                            scope.launch {
                                                val result =
                                                    GameSaveBackupManager.restoreFromGoogle(
                                                        this@UnifiedActivity,
                                                        detailGameSource,
                                                        detailGameId,
                                                        app.name,
                                                    )
                                                isWorking = false
                                                com.winlator.cmod.shared.android.AppUtils.showToast(
                                                    context,
                                                    result.message,
                                                    android.widget.Toast.LENGTH_SHORT,
                                                )
                                            }
                                        }
                                    },
                                    onBack = { currentScreen = LibraryDetailScreen.Main },
                                )
                            }

                            LibraryDetailScreen.Uninstall -> {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp, vertical = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        stringResource(
                                            if (isCustom) R.string.library_games_remove_game else R.string.library_games_uninstall_game,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.1.sp,
                                    )

                                    Spacer(Modifier.weight(1f))

                                    UninstallConfirmation(
                                        message =
                                            if (isCustom) {
                                                getString(R.string.library_games_remove_confirm, app.name)
                                            } else {
                                                getString(R.string.library_games_uninstall_confirm, app.name)
                                            },
                                        confirmLabel =
                                            stringResource(
                                                if (isCustom) R.string.common_ui_remove else R.string.common_ui_uninstall,
                                            ),
                                        onConfirm = {
                                            if (isGog) {
                                                scope.launch(Dispatchers.IO) {
                                                    GOGService.deleteGame(
                                                        context,
                                                        LibraryItem(
                                                            "GOG_${gogGame!!.id}",
                                                            gogGame.title,
                                                            com.winlator.cmod.feature.stores.steam.enums.GameSource.GOG,
                                                        ),
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        com.winlator.cmod.shared.android.AppUtils.showToast(
                                                            context,
                                                            getString(R.string.library_games_game_uninstalled, app.name),
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        )
                                                        onDismissRequest()
                                                    }
                                                }
                                            } else if (isCustom) {
                                                scope.launch(Dispatchers.IO) {
                                                    val cm = ContainerManager(context)
                                                    val sc = findLibraryShortcutForGame(cm, app, isCustom, isEpic, epicId)
                                                    sc?.let { LibraryShortcutUtils.deleteShortcutArtifacts(context, it) }
                                                    java.io
                                                        .File(
                                                            context.filesDir,
                                                            "custom_icons/${app.name.replace("/", "_")}.png",
                                                        ).delete()
                                                    PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(app.id))
                                                    withContext(Dispatchers.Main) {
                                                        com.winlator.cmod.shared.android.AppUtils.showToast(
                                                            context,
                                                            getString(R.string.library_games_game_removed, app.name),
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        )
                                                        onDismissRequest()
                                                    }
                                                }
                                            } else if (isEpic) {
                                                scope.launch(Dispatchers.IO) {
                                                    val result = EpicService.deleteGame(context, epicId)
                                                    withContext(Dispatchers.Main) {
                                                        if (result.isSuccess) {
                                                            com.winlator.cmod.shared.android.AppUtils.showToast(
                                                                context,
                                                                getString(R.string.library_games_game_uninstalled, app.name),
                                                                android.widget.Toast.LENGTH_SHORT,
                                                            )
                                                        } else {
                                                            com.winlator.cmod.shared.android.AppUtils.showToast(
                                                                context,
                                                                getString(
                                                                    R.string.library_games_failed_to_uninstall_reason,
                                                                    result.exceptionOrNull()?.message ?: "",
                                                                ),
                                                                android.widget.Toast.LENGTH_LONG,
                                                            )
                                                        }
                                                        onDismissRequest()
                                                    }
                                                }
                                            } else {
                                                SteamService.uninstallApp(app.id) { success ->
                                                    if (success) {
                                                        com.winlator.cmod.shared.android.AppUtils.showToast(
                                                            context,
                                                            getString(R.string.library_games_game_uninstalled, app.name),
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        )
                                                    } else {
                                                        com.winlator.cmod.shared.android.AppUtils.showToast(
                                                            context,
                                                            getString(R.string.library_games_failed_to_uninstall),
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        )
                                                    }
                                                    onDismissRequest()
                                                }
                                            }
                                        },
                                        onCancel = { currentScreen = LibraryDetailScreen.Main },
                                    )
                                }
                            }
                        }
                    }

                    // Close button overlay
                    IconButton(
                        onClick = onDismissRequest,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(42.dp)
                                .shadow(8.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                                .clip(CircleShape)
                                .background(BgDark.copy(alpha = 0.7f)),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }
            }
        }
    }

    @Composable
    private fun DetailCard(
        label: String,
        value: String,
        modifier: Modifier = Modifier.fillMaxWidth(),
        valueColor: Color? = null,
        onClick: (() -> Unit)? = null,
    ) {
        Surface(
            modifier =
                modifier
                    .then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier),
            color = SurfaceDark,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (onClick != null) Accent.copy(alpha = 0.25f) else CardBorder),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontSize = 10.sp,
                    )
                    if (onClick != null) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = Accent.copy(alpha = 0.6f),
                        )
                    }
                }
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = valueColor ?: (if (onClick != null) Accent else TextPrimary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    private fun PlayButton(onClick: () -> Unit) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
            label = "playScale",
        )

        // Idle glow pulse
        val infiniteTransition = rememberInfiniteTransition(label = "playGlow")
        val glowPulse by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.6f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween<Float>(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "playPulse",
        )

        val baseGradient =
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color(0xFF00B4D8),
                        Accent,
                        Color(0xFF7B2FF7),
                    ),
            )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = Accent.copy(alpha = glowPulse),
                        spotColor = Accent.copy(alpha = glowPulse),
                    ).clip(RoundedCornerShape(12.dp))
                    .background(baseGradient)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.library_games_play),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }

    @Composable
    private fun InstallButton(
        loading: Boolean = false,
        onClick: () -> Unit,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed && !loading) 0.92f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
            label = "installScale",
        )
        val infiniteTransition = rememberInfiniteTransition(label = "installGlow")
        val glowPulse by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.6f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween<Float>(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "installPulse",
        )
        val baseGradient =
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color(0xFF00B4D8),
                        Accent,
                        Color(0xFF7B2FF7),
                    ),
            )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = Accent.copy(alpha = glowPulse),
                        spotColor = Accent.copy(alpha = glowPulse),
                    ).clip(RoundedCornerShape(12.dp))
                    .background(baseGradient)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { if (!loading) onClick() },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.common_ui_download),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }

    @Composable
    private fun CompactActionButton(
        icon: ImageVector,
        label: String,
        tint: Color = TextPrimary,
        bgColor: Color = SurfaceDark,
        modifier: Modifier = Modifier,
        height: Dp = 36.dp,
        fontSize: TextUnit = 13.sp,
        onClick: () -> Unit,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.93f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
            label = "btnScale",
        )
        val glowAlpha by animateFloatAsState(
            targetValue = if (isPressed) 0.18f else 0f,
            animationSpec = tween(durationMillis = 120),
            label = "btnGlow",
        )
        Surface(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(height)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ),
            color = bgColor,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, tint.copy(alpha = glowAlpha)),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
                Spacer(Modifier.width(6.dp))
                Text(label, color = tint, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }

    // Single game capsule for carousel / grid / list
    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    private fun GameCapsule(
        app: SteamApp,
        gogGame: GOGGame? = null,
        iconRefreshKey: Int = 0,
        isFocusedOverride: Boolean = false,
        isControllerActive: Boolean = false,
        shortcuts: List<Shortcut> = emptyList(),
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        useLibraryCapsule: Boolean = false,
        listMode: Boolean = false,
        modifier: Modifier = Modifier,
    ) {
        val context = LocalContext.current
        val isCustom = app.id < 0
        val isEpic = app.id >= 2000000000
        val epicId = if (isEpic) app.id - 2000000000 else 0
        val epicGame by produceState<EpicGame?>(initialValue = null, key1 = epicId) {
            value = if (isEpic) db.epicGameDao().getById(epicId) else null
        }
        // Use pre-cached shortcuts list instead of loading from disk per-item
        val customLibraryIconPath =
            remember(app.id, gogGame?.id, iconRefreshKey, shortcuts) {
                val shortcut =
                    if (gogGame != null) {
                        shortcuts.find {
                            it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == gogGame.id
                        }
                    } else {
                        findShortcutForGame(shortcuts, app, isCustom, isEpic, epicId)
                    }
                val customPath =
                    shortcut
                        ?.getExtra("customLibraryIconPath")
                        ?.ifBlank { shortcut.getExtra("customCoverArtPath") }
                customPath?.takeIf { it.isNotBlank() && java.io.File(it).exists() }
            }
        val defaultClick: () -> Unit = {
            val containerManager =
                com.winlator.cmod.runtime.container
                    .ContainerManager(context)
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
        val clickInteraction = remember { MutableInteractionSource() }
        val isPressed by clickInteraction.collectIsPressedAsState()
        val isFocused = isControllerActive && isFocusedOverride
        val glowAlpha by animateFloatAsState(
            targetValue = if (isPressed) 0.7f else 0f,
            animationSpec = if (isPressed) tween(100) else tween(400),
            label = "capsuleGlow",
        )
        val clickModifier =
            Modifier
                .then(
                    if (glowAlpha > 0f) {
                        Modifier.drawWithContent {
                            drawContent()
                            drawRoundRect(
                                color = AccentGlow,
                                alpha = glowAlpha * 0.25f,
                                cornerRadius = CornerRadius(12.dp.toPx()),
                            )
                        }
                    } else {
                        Modifier
                    },
                ).combinedClickable(
                    interactionSource = clickInteraction,
                    indication = null,
                    onClick = onClick ?: defaultClick,
                    onLongClick = onLongClick,
                )

        @Composable
        fun ArtContent(artModifier: Modifier) {
            val customArtworkFile =
                customLibraryIconPath
                    ?.let { java.io.File(it) }
                    ?.takeIf { it.exists() }

            if (customArtworkFile != null) {
                val customArtworkCacheKey =
                    "library_custom_icon:${customArtworkFile.absolutePath}:${customArtworkFile.lastModified()}"
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(customArtworkFile)
                            .memoryCacheKey(customArtworkCacheKey)
                            .diskCacheKey(customArtworkCacheKey)
                            .crossfade(300)
                            .build(),
                    contentDescription = app.name,
                    modifier = artModifier,
                    contentScale = ContentScale.Crop,
                )
            } else if (isCustom) {
                val safeName = app.name.replace("/", "_").replace("\\", "_")
                val iconFile = java.io.File(context.filesDir, "custom_icons/$safeName.png")
                if (iconFile.exists()) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(context)
                                .data(iconFile)
                                .crossfade(300)
                                .build(),
                        contentDescription = app.name,
                        modifier = artModifier,
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = artModifier.background(SurfaceDark),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.SportsEsports,
                            contentDescription = app.name,
                            tint = Accent.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            } else if (gogGame != null) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(gogGame.imageUrl.ifEmpty { gogGame.iconUrl })
                            .crossfade(300)
                            .build(),
                    contentDescription = app.name,
                    modifier = artModifier,
                    contentScale = ContentScale.Crop,
                )
            } else if (isEpic) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(epicGame?.primaryImageUrl ?: epicGame?.iconUrl)
                            .crossfade(300)
                            .build(),
                    contentDescription = app.name,
                    modifier = artModifier,
                    contentScale = ContentScale.Crop,
                )
            } else {
                val imageUrl =
                    when {
                        listMode -> app.getSmallCapsuleUrl()
                        useLibraryCapsule -> app.getLibraryCapsuleUrl()
                        else -> app.getCapsuleUrl()
                    }
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(imageUrl)
                            .crossfade(300)
                            .build(),
                    contentDescription = app.name,
                    modifier = artModifier,
                    contentScale = ContentScale.Crop,
                )
            }
        }

        if (listMode) {
            // Horizontal row card with hero background
            val heroUrl = if (!isCustom && gogGame == null && !isEpic) app.getHeroUrl() else null

            Box(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, if (isControllerActive) CardBorder else Color.Transparent, RoundedCornerShape(14.dp))
                        .chasingBorder(
                            isFocused = isFocused,
                            paused = chasingBordersPaused.value || !libraryTabActive.value,
                            cornerRadius = 14.dp,
                        ).background(CardDark, RoundedCornerShape(14.dp))
                        .focusable()
                        .then(clickModifier),
            ) {
                // Hero background layer (falls back to CardDark if image fails)
                if (heroUrl != null) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(context)
                                .data(heroUrl)
                                .crossfade(300)
                                .build(),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = 0.25f },
                        contentScale = ContentScale.Crop,
                    )
                }

                // Foreground content
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .height(52.dp)
                                .aspectRatio(462f / 174f)
                                .clip(RoundedCornerShape(8.dp)),
                    ) {
                        ArtContent(Modifier.fillMaxSize())
                    }

                    Spacer(Modifier.width(14.dp))

                    Text(
                        text = app.name,
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(if (isFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            // Vertical card: art on top, title below
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .border(1.dp, CardDark, RoundedCornerShape(12.dp))
                        .chasingBorder(
                            isFocused = isFocused,
                            paused = chasingBordersPaused.value || !libraryTabActive.value,
                            cornerRadius = 12.dp,
                        ).background(CardDark, RoundedCornerShape(12.dp))
                        .focusable()
                        .then(clickModifier),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                ) {
                    ArtContent(Modifier.fillMaxSize())
                }

                Text(
                    text = app.name,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .then(if (isFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    // Epic Store Tab
    @Composable
    fun EpicStoreTab(
        isLoggedIn: Boolean,
        searchQuery: String = "",
        layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID_4,
        onLoginClick: () -> Unit,
    ) {
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

        val displayedApps =
            remember(epicApps, searchQuery) {
                if (searchQuery.isBlank()) {
                    epicApps
                } else {
                    epicApps.filter { it.title.contains(searchQuery, ignoreCase = true) }
                }
            }

        // Sync store focus infrastructure
        LaunchedEffect(displayedApps.size) {
            activity?.storeItemCount = displayedApps.size
            val lastIndex = (displayedApps.size - 1).coerceAtLeast(0)
            if (activity != null && displayedApps.isNotEmpty() && activity.storeFocusIndex.value > lastIndex) {
                activity.storeFocusIndex.value = lastIndex
            }
        }
        DisposableEffect(displayedApps) {
            activity?.storeItemClickCallback = { idx ->
                displayedApps.getOrNull(idx)?.let { selectedAppId.value = it.id }
            }
            activity?.storeGridState = gridState
            onDispose {
                activity?.storeItemClickCallback = null
                activity?.storeGridState = null
            }
        }

        if (layoutMode == LibraryLayoutMode.LIST) {
            val listViewState = rememberLazyListState()
            JoystickListScroll(listViewState, activity?.rightStickScrollState)
            ListView(
                items = displayedApps,
                modifier = Modifier.tabScreenPadding(),
                listState = listViewState,
                contentPadding = TabListContentPadding,
            ) { app, _, _ ->
                EpicStoreCapsule(app, listMode = true, isControllerActive = ControllerHelper.isControllerConnected()) {
                    selectedAppId.value =
                        app.id
                }
            }
        } else {
            val focusIndex by (activity?.storeFocusIndex ?: kotlinx.coroutines.flow.MutableStateFlow(0)).collectAsState()
            val focusRequesters =
                remember(displayedApps.size) {
                    List(displayedApps.size) { FocusRequester() }
                }
            LaunchedEffect(focusIndex, focusRequesters.size) {
                if (searchQuery.isEmpty() && focusRequesters.isNotEmpty() && focusIndex in focusRequesters.indices) {
                    gridState.animateScrollToItem(focusIndex)
                    try {
                        focusRequesters[focusIndex].requestFocus()
                    } catch (_: Exception) {
                    }
                }
            }
            JoystickGridScroll(gridState, activity?.rightStickScrollState)
            FourByTwoGridView(
                items = displayedApps,
                modifier = Modifier.tabScreenPadding(top = TabGridTopPadding),
                gridState = gridState,
            ) { app, index, rowHeight ->
                Box(
                    Modifier.height(rowHeight).then(
                        if (index in focusRequesters.indices) {
                            Modifier.focusRequester(focusRequesters[index])
                        } else {
                            Modifier
                        },
                    ),
                ) {
                    EpicStoreCapsule(
                        app,
                        isFocusedOverride = index == focusIndex,
                        isControllerActive = ControllerHelper.isControllerConnected(),
                    ) {
                        selectedAppId.value =
                            app.id
                    }
                }
            }
        }

        val selectedApp = epicApps.find { it.id == selectedAppId.value }
        if (selectedApp != null) {
            EpicGameManagerDialog(
                app = selectedApp,
                onDismissRequest = { selectedAppId.value = null },
            )
        }
    }

    @Composable
    fun EpicStoreCapsule(
        app: com.winlator.cmod.feature.stores.epic.data.EpicGame,
        listMode: Boolean = false,
        isFocusedOverride: Boolean = false,
        isControllerActive: Boolean = false,
        onClick: () -> Unit,
    ) {
        val context = LocalContext.current
        var isFocused by remember { mutableStateOf(false) }
        val clickInteraction = remember { MutableInteractionSource() }
        val isPressed by clickInteraction.collectIsPressedAsState()
        val glowAlpha by animateFloatAsState(
            targetValue = if (isPressed) 0.7f else 0f,
            animationSpec = if (isPressed) tween(100) else tween(400),
            label = "epicCapsuleGlow",
        )
        val effectiveFocus = isControllerActive && (isFocusedOverride || isFocused)
        val isInstalled = app.installPath != null && java.io.File(app.installPath!!).exists()
        val imageUrl = app.primaryImageUrl ?: app.iconUrl

        val borderColor = if (isControllerActive) CardBorder else Color.Transparent

        if (listMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                        .chasingBorder(isFocused = effectiveFocus, paused = chasingBordersPaused.value, cornerRadius = 14.dp)
                        .background(CardDark, RoundedCornerShape(14.dp))
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .then(
                            if (glowAlpha > 0f) {
                                Modifier.drawWithContent {
                                    drawContent()
                                    drawRoundRect(color = AccentGlow, alpha = glowAlpha * 0.25f, cornerRadius = CornerRadius(14.dp.toPx()))
                                }
                            } else {
                                Modifier
                            },
                        ).clickable(interactionSource = clickInteraction, indication = null, onClick = onClick)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .height(52.dp)
                        .aspectRatio(462f / 174f)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(context)
                                .data(imageUrl)
                                .crossfade(300)
                                .build(),
                        contentDescription = app.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (isInstalled) {
                        Box(
                            Modifier
                                .align(
                                    Alignment.BottomEnd,
                                ).padding(4.dp)
                                .background(SurfaceDark.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(3.dp),
                        ) {
                            Text(
                                stringResource(R.string.library_games_installed_badge),
                                color = StatusOnline,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    app.title,
                    modifier =
                        Modifier
                            .weight(1f)
                            .then(if (effectiveFocus) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .chasingBorder(isFocused = effectiveFocus, paused = chasingBordersPaused.value, cornerRadius = 16.dp)
                        .background(CardDark, RoundedCornerShape(16.dp))
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .then(
                            if (glowAlpha > 0f) {
                                Modifier.drawWithContent {
                                    drawContent()
                                    drawRoundRect(color = AccentGlow, alpha = glowAlpha * 0.25f, cornerRadius = CornerRadius(16.dp.toPx()))
                                }
                            } else {
                                Modifier
                            },
                        ).clickable(interactionSource = clickInteraction, indication = null, onClick = onClick),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                ) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(context)
                                .data(imageUrl)
                                .crossfade(300)
                                .build(),
                        contentDescription = app.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    if (isInstalled) {
                        Box(
                            Modifier
                                .align(
                                    Alignment.BottomEnd,
                                ).padding(8.dp)
                                .background(SurfaceDark.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(4.dp),
                        ) {
                            Text(
                                stringResource(R.string.library_games_installed_badge),
                                color = StatusOnline,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Text(
                    app.title,
                    modifier =
                        Modifier
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .then(if (effectiveFocus) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    @Composable
    private fun StoreInstallDialogShell(
        title: String,
        heroImageUrl: String?,
        subtitle: String,
        sourceLabel: String = "",
        onDismissRequest: () -> Unit,
        infoContent: @Composable ColumnScope.() -> Unit = {},
        actionsContent: @Composable ColumnScope.() -> Unit,
    ) {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.864f).fillMaxHeight(0.92f),
                shape = RoundedCornerShape(20.dp),
                color = CardDark,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.42f),
                        ) {
                            AsyncImage(
                                model =
                                    ImageRequest
                                        .Builder(LocalContext.current)
                                        .data(heroImageUrl)
                                        .crossfade(150)
                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .build(),
                                contentDescription = "$title artwork",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillWidth,
                                alignment = Alignment.TopCenter,
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colorStops =
                                                    arrayOf(
                                                        0.0f to Color.Transparent,
                                                        0.45f to Color.Transparent,
                                                        0.72f to CardDark.copy(alpha = 0.72f),
                                                        1.0f to CardDark,
                                                    ),
                                            ),
                                        ),
                            )
                            Column(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 24.dp, end = 80.dp, bottom = 24.dp),
                            ) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (subtitle.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                            ) {
                                if (sourceLabel.isNotBlank()) {
                                    Surface(
                                        color = Accent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text(
                                            sourceLabel,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            color = Accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                infoContent()
                            }

                            Column(
                                modifier =
                                    Modifier
                                        .widthIn(min = 200.dp, max = 260.dp)
                                        .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                            ) {
                                actionsContent()
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(42.dp)
                                .shadow(8.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                                .clip(CircleShape)
                                .background(BgDark.copy(alpha = 0.7f)),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }
            }
        }
    }

    @Composable
    fun EpicGameManagerDialog(
        app: EpicGame,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val installed = app.isInstalled && java.io.File(app.installPath).exists()
        val scope = rememberCoroutineScope()

        var isLoading by remember { mutableStateOf(!installed) }
        var manifestSizes by remember { mutableStateOf<EpicManager.ManifestSizes?>(null) }
        var dlcApps by remember { mutableStateOf<List<EpicGame>>(emptyList()) }
        val selectedDlcIds = remember { mutableStateListOf<Int>() }
        var customPath by remember { mutableStateOf<String?>(null) }
        var showCustomPathWarning by remember { mutableStateOf(false) }
        var showDlcDialog by remember { mutableStateOf(false) }

        val folderPickerLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri -> uri?.let { customPath = getPathFromTreeUri(it) } }

        if (showCustomPathWarning) {
            CustomPathWarningDialog(
                onDismiss = { showCustomPathWarning = false },
                onProceed = {
                    showCustomPathWarning = false
                    folderPickerLauncher.launch(null)
                },
            )
        }

        if (showDlcDialog && dlcApps.isNotEmpty()) {
            GameSettingsDialogFrame(
                title = stringResource(R.string.library_games_dlcs),
                onDismissRequest = { showDlcDialog = false },
            ) {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    dlcApps.forEachIndexed { index, dlc ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = CardBorder.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        if (selectedDlcIds.contains(dlc.id)) {
                                            selectedDlcIds.remove(dlc.id)
                                        } else {
                                            selectedDlcIds.add(dlc.id)
                                        }
                                    }.padding(horizontal = 16.dp, vertical = 2.dp),
                        ) {
                            Checkbox(
                                checked = selectedDlcIds.contains(dlc.id),
                                onCheckedChange = { if (it) selectedDlcIds.add(dlc.id) else selectedDlcIds.remove(dlc.id) },
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = Accent,
                                        uncheckedColor = TextSecondary,
                                        checkmarkColor = Color.White,
                                    ),
                            )
                            Text(dlc.title, color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
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
        val defaultPathSet =
            if (PrefManager.useSingleDownloadFolder) {
                PrefManager.defaultDownloadFolder.isNotEmpty()
            } else {
                PrefManager.epicDownloadFolder
                    .isNotEmpty()
            }
        val effectivePath = customPath ?: EpicConstants.getGameInstallPath(context, app.appName)
        val availableBytes =
            try {
                StorageUtils.getAvailableSpace(effectivePath)
            } catch (e: Exception) {
                0L
            }
        val isInstallEnabled = installed || availableBytes >= totalInstallSize
        val installPathDisplay = customPath ?: EpicConstants.defaultEpicGamesPath(context)

        StoreInstallDialogShell(
            title = app.title,
            heroImageUrl = app.artPortrait.ifEmpty { app.primaryImageUrl },
            subtitle =
                listOfNotNull(
                    app.developer.takeIf { it.isNotBlank() },
                    app.publisher.takeIf { it.isNotBlank() },
                ).joinToString(" • "),
            sourceLabel = "Epic Games",
            onDismissRequest = onDismissRequest,
            infoContent = {
                if (isLoading && !installed) {
                    Spacer(Modifier.height(18.dp))
                    CircularProgressIndicator(color = Accent)
                } else if (installed) {
                    DetailCard(
                        label = stringResource(R.string.library_games_install_path),
                        value = app.installPath,
                    )
                    DetailCard(
                        label = stringResource(R.string.common_ui_status),
                        value = stringResource(R.string.common_ui_installed),
                        valueColor = StatusOnline,
                    )
                } else {
                    DetailCard(
                        label = stringResource(R.string.library_games_install_path),
                        value = installPathDisplay,
                    )
                    DetailCard(
                        stringResource(R.string.library_games_download_slash_install),
                        stringResource(
                            R.string.library_games_download_install_available,
                            StorageUtils.formatBinarySize(totalDownloadSize),
                            StorageUtils.formatBinarySize(totalInstallSize),
                            StorageUtils.formatBinarySize(availableBytes),
                        ),
                        valueColor = if (!isInstallEnabled) DangerRed else null,
                    )
                }
            },
        ) {
            if (installed) {
                PlayButton(onClick = {
                    launchEpicGame(context, ContainerManager(context), app)
                    onDismissRequest()
                })
                if (app.cloudSaveEnabled) {
                    CompactActionButton(
                        icon = Icons.Outlined.CloudSync,
                        label = stringResource(R.string.google_cloud_title),
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                EpicCloudSavesManager.syncCloudSaves(context, app.id, "auto")
                            }
                            onDismissRequest()
                            com.winlator.cmod.shared.android.AppUtils.showToast(
                                context,
                                context.getString(R.string.google_cloud_sync_started),
                                android.widget.Toast.LENGTH_SHORT,
                            )
                        },
                    )
                }
                CompactActionButton(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(R.string.common_ui_uninstall),
                    tint = DangerRed,
                    bgColor = DangerRed.copy(alpha = 0.12f),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            EpicService.deleteGame(context, app.id)
                        }
                        onDismissRequest()
                    },
                )
            } else {
                InstallButton(
                    loading = isLoading,
                    onClick = {
                        val installPath =
                            if (customPath != null) {
                                val sanitizedTitle = app.title.replace(Regex("[^a-zA-Z0-9 \\-_]"), "").trim()
                                java.io.File(customPath!!, sanitizedTitle).absolutePath
                            } else {
                                EpicConstants.getGameInstallPath(context, app.title)
                            }
                        EpicService.downloadGame(context, app.id, selectedDlcIds.toList(), installPath, "en-US")
                        onDismissRequest()
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CompactActionButton(
                        icon = Icons.Outlined.Folder,
                        label =
                            if (customPath !=
                                null
                            ) {
                                stringResource(R.string.common_ui_custom)
                            } else if (defaultPathSet) {
                                stringResource(R.string.common_ui_already_set)
                            } else {
                                stringResource(R.string.common_ui_custom)
                            },
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (customPath == null && defaultPathSet) {
                                showCustomPathWarning = true
                            } else {
                                folderPickerLauncher.launch(null)
                            }
                        },
                    )
                    if (dlcApps.isNotEmpty()) {
                        CompactActionButton(
                            icon = Icons.Outlined.Extension,
                            label = stringResource(R.string.library_games_dlcs),
                            modifier = Modifier.weight(1f),
                            onClick = { showDlcDialog = true },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun GOGStoreTab(
        isLoggedIn: Boolean,
        searchQuery: String = "",
        layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID_4,
        onLoginClick: () -> Unit,
    ) {
        if (!isLoggedIn) {
            LoginRequiredScreen("GOG", onLoginClick)
            return
        }

        val gogApps by db.gogGameDao().getAll().collectAsState(initial = emptyList())
        val selectedGameId = remember { mutableStateOf<String?>(null) }
        val gridState = rememberLazyGridState()
        val activity = LocalContext.current as? UnifiedActivity

        val displayedApps =
            remember(gogApps, searchQuery) {
                if (searchQuery.isBlank()) {
                    gogApps
                } else {
                    gogApps.filter { it.title.contains(searchQuery, ignoreCase = true) }
                }
            }

        // Sync store focus infrastructure
        LaunchedEffect(displayedApps.size) {
            activity?.storeItemCount = displayedApps.size
            val lastIndex = (displayedApps.size - 1).coerceAtLeast(0)
            if (activity != null && displayedApps.isNotEmpty() && activity.storeFocusIndex.value > lastIndex) {
                activity.storeFocusIndex.value = lastIndex
            }
        }
        DisposableEffect(displayedApps) {
            activity?.storeItemClickCallback = { idx ->
                displayedApps.getOrNull(idx)?.let { selectedGameId.value = it.id }
            }
            activity?.storeGridState = gridState
            onDispose {
                activity?.storeItemClickCallback = null
                activity?.storeGridState = null
            }
        }

        val isControllerActive = ControllerHelper.isControllerConnected()
        val gogBorderColor = if (isControllerActive) CardBorder else Color.Transparent

        if (layoutMode == LibraryLayoutMode.LIST) {
            val listViewState = rememberLazyListState()
            ListView(
                items = displayedApps,
                modifier = Modifier.tabScreenPadding(),
                listState = listViewState,
                contentPadding = TabListContentPadding,
            ) { app, _, _ ->
                val isInstalled = app.isInstalled && java.io.File(app.installPath).exists()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, gogBorderColor, RoundedCornerShape(14.dp))
                            .background(CardDark, RoundedCornerShape(14.dp))
                            .clickable { selectedGameId.value = app.id }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .height(52.dp)
                            .aspectRatio(462f / 174f)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(LocalContext.current)
                                    .data(app.imageUrl.ifEmpty { app.iconUrl })
                                    .crossfade(300)
                                    .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        if (isInstalled) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Installed",
                                tint = StatusOnline,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = app.title,
                        modifier = Modifier.weight(1f),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            val focusIndex by (activity?.storeFocusIndex ?: kotlinx.coroutines.flow.MutableStateFlow(0)).collectAsState()
            val focusRequesters =
                remember(displayedApps.size) {
                    List(displayedApps.size) { FocusRequester() }
                }
            LaunchedEffect(focusIndex, focusRequesters.size) {
                if (searchQuery.isEmpty() && focusRequesters.isNotEmpty() && focusIndex in focusRequesters.indices) {
                    gridState.animateScrollToItem(focusIndex)
                    try {
                        focusRequesters[focusIndex].requestFocus()
                    } catch (_: Exception) {
                    }
                }
            }
            FourByTwoGridView(
                items = displayedApps,
                modifier = Modifier.tabScreenPadding(top = TabGridTopPadding),
                gridState = gridState,
            ) { app, index, rowHeight ->
                val isInstalled = app.isInstalled && java.io.File(app.installPath).exists()
                val isItemFocused = isControllerActive && index == focusIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .then(
                                if (index in focusRequesters.indices) {
                                    Modifier.focusRequester(focusRequesters[index])
                                } else {
                                    Modifier
                                },
                            ).border(1.dp, gogBorderColor, RoundedCornerShape(16.dp))
                            .chasingBorder(isFocused = isItemFocused, paused = chasingBordersPaused.value, cornerRadius = 16.dp)
                            .background(CardDark, RoundedCornerShape(16.dp))
                            .clickable { selectedGameId.value = app.id },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    ) {
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(LocalContext.current)
                                    .data(app.imageUrl.ifEmpty { app.iconUrl })
                                    .crossfade(300)
                                    .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        if (isInstalled) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Installed",
                                tint = StatusOnline,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(24.dp),
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
                        textAlign = TextAlign.Center,
                    )
                }
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
    fun GOGGameManagerDialog(
        app: GOGGame,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val installed = app.isInstalled && java.io.File(app.installPath).exists()
        val scope = rememberCoroutineScope()
        var customPath by remember { mutableStateOf<String?>(null) }
        var showCustomPathWarning by remember { mutableStateOf(false) }

        val folderPickerLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri -> uri?.let { customPath = getPathFromTreeUri(it) } }

        if (showCustomPathWarning) {
            CustomPathWarningDialog(
                onDismiss = { showCustomPathWarning = false },
                onProceed = {
                    showCustomPathWarning = false
                    folderPickerLauncher.launch(null)
                },
            )
        }

        val defaultPathSet =
            if (PrefManager.useSingleDownloadFolder) {
                PrefManager.defaultDownloadFolder.isNotEmpty()
            } else {
                PrefManager.gogDownloadFolder
                    .isNotEmpty()
            }
        val installRootPath = customPath ?: GOGConstants.defaultGOGGamesPath
        val installPathDisplay =
            if (customPath != null) {
                java.io.File(customPath!!, GOGConstants.getSanitizedGameFolderName(app.title)).absolutePath
            } else {
                GOGConstants.getGameInstallPath(app.title)
            }
        val requiredBytes = maxOf(app.installSize, app.downloadSize)
        val availableBytes =
            try {
                StorageUtils.getAvailableSpace(installRootPath)
            } catch (_: Exception) {
                0L
            }
        val isInstallEnabled = installed || availableBytes >= requiredBytes

        StoreInstallDialogShell(
            title = app.title,
            heroImageUrl = app.imageUrl.ifEmpty { app.iconUrl },
            subtitle =
                listOfNotNull(
                    app.developer.takeIf { it.isNotBlank() },
                    app.publisher.takeIf { it.isNotBlank() },
                ).joinToString(" • "),
            sourceLabel = "GOG",
            onDismissRequest = onDismissRequest,
            infoContent = {
                if (installed) {
                    DetailCard(
                        label = stringResource(R.string.library_games_install_path),
                        value = app.installPath,
                    )
                    DetailCard(
                        label = stringResource(R.string.common_ui_status),
                        value = stringResource(R.string.common_ui_installed),
                        valueColor = StatusOnline,
                    )
                } else {
                    DetailCard(
                        label = stringResource(R.string.library_games_install_path),
                        value = installPathDisplay,
                    )
                    DetailCard(
                        stringResource(R.string.library_games_download_slash_install),
                        stringResource(
                            R.string.library_games_download_install_available,
                            StorageUtils.formatBinarySize(app.downloadSize),
                            StorageUtils.formatBinarySize(app.installSize),
                            StorageUtils.formatBinarySize(availableBytes),
                        ),
                        valueColor = if (!isInstallEnabled) DangerRed else null,
                    )
                }
            },
        ) {
            if (installed) {
                PlayButton(onClick = {
                    launchGogGame(context, ContainerManager(context), app)
                    onDismissRequest()
                })
                CompactActionButton(
                    icon = Icons.Outlined.CloudSync,
                    label = stringResource(R.string.google_cloud_title),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            GOGService.syncCloudSaves(context, "GOG_${app.id}", "auto")
                        }
                        onDismissRequest()
                        com.winlator.cmod.shared.android.AppUtils.showToast(
                            context,
                            context.getString(R.string.google_cloud_sync_started),
                            android.widget.Toast.LENGTH_SHORT,
                        )
                    },
                )
                CompactActionButton(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(R.string.common_ui_uninstall),
                    tint = DangerRed,
                    bgColor = DangerRed.copy(alpha = 0.12f),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            GOGService.deleteGame(
                                context,
                                LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.feature.stores.steam.enums.GameSource.GOG),
                            )
                            withContext(Dispatchers.Main) { onDismissRequest() }
                        }
                    },
                )
            } else {
                InstallButton(
                    onClick = {
                        GOGService.downloadGame(context, app.id, installPathDisplay, PrefManager.containerLanguage)
                        onDismissRequest()
                    },
                )
                CompactActionButton(
                    icon = Icons.Outlined.Folder,
                    label =
                        if (customPath !=
                            null
                        ) {
                            stringResource(R.string.common_ui_custom)
                        } else if (defaultPathSet) {
                            stringResource(R.string.common_ui_already_set)
                        } else {
                            stringResource(R.string.common_ui_custom)
                        },
                    onClick = {
                        if (customPath == null && defaultPathSet) {
                            showCustomPathWarning = true
                        } else {
                            folderPickerLauncher.launch(null)
                        }
                    },
                )
            }
        }
    }

    // Steam Store Tab
    @Composable
    fun SteamStoreTab(
        isLoggedIn: Boolean,
        steamApps: List<SteamApp>,
        searchQuery: String = "",
        layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID_4,
    ) {
        if (!isLoggedIn && !SteamService.hasStoredCredentials(this)) {
            LoginRequiredScreen("Steam") {
                startActivity(Intent(this@UnifiedActivity, SteamLoginActivity::class.java))
            }
            return
        }

        var selectedAppForDialog by remember { mutableStateOf<SteamApp?>(null) }
        val gridState = rememberLazyGridState()
        val activity = LocalContext.current as? UnifiedActivity

        val displayedApps =
            remember(steamApps, searchQuery) {
                if (searchQuery.isBlank()) {
                    steamApps
                } else {
                    steamApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }
            }

        // Sync store focus infrastructure
        LaunchedEffect(displayedApps.size) {
            activity?.storeItemCount = displayedApps.size
            val lastIndex = (displayedApps.size - 1).coerceAtLeast(0)
            if (activity != null && displayedApps.isNotEmpty() && activity.storeFocusIndex.value > lastIndex) {
                activity.storeFocusIndex.value = lastIndex
            }
        }
        // Register A-button click callback and grid state for visible-area snapping
        DisposableEffect(displayedApps) {
            activity?.storeItemClickCallback = { idx ->
                displayedApps.getOrNull(idx)?.let { selectedAppForDialog = it }
            }
            activity?.storeGridState = gridState
            onDispose {
                activity?.storeItemClickCallback = null
                activity?.storeGridState = null
            }
        }

        if (layoutMode == LibraryLayoutMode.LIST) {
            val listViewState = rememberLazyListState()
            JoystickListScroll(listViewState, activity?.rightStickScrollState, minSpeed = 2.5f, maxSpeed = 16f, quadratic = true)
            ListView(
                items = displayedApps,
                modifier = Modifier.tabScreenPadding(),
                listState = listViewState,
                contentPadding = TabListContentPadding,
            ) { app, _, _ ->
                SteamStoreCapsule(app, listMode = true, isControllerActive = ControllerHelper.isControllerConnected(), onClick = {
                    selectedAppForDialog =
                        app
                })
            }
        } else {
            val focusIndex by (activity?.storeFocusIndex ?: kotlinx.coroutines.flow.MutableStateFlow(0)).collectAsState()
            val focusRequesters =
                remember(displayedApps.size) {
                    List(displayedApps.size) { FocusRequester() }
                }
            LaunchedEffect(focusIndex, focusRequesters.size) {
                if (searchQuery.isEmpty() && focusRequesters.isNotEmpty() && focusIndex in focusRequesters.indices) {
                    gridState.animateScrollToItem(focusIndex)
                    try {
                        focusRequesters[focusIndex].requestFocus()
                    } catch (_: Exception) {
                    }
                }
            }
            // Right joystick: 2x faster at full push with quadratic speed curve
            JoystickGridScroll(gridState, activity?.rightStickScrollState, minSpeed = 2.5f, maxSpeed = 16f, quadratic = true)
            // Left joystick: 75% slower scrolling (vertical only, for browsing store)
            JoystickGridScroll(gridState, activity?.leftStickScrollState, deadZone = 0.15f, minSpeed = 0.3125f, maxSpeed = 2f)
            FourByTwoGridView(
                items = displayedApps,
                modifier = Modifier.tabScreenPadding(top = TabGridTopPadding),
                gridState = gridState,
            ) { app, index, rowHeight ->
                Box(
                    Modifier.height(rowHeight).then(
                        if (index in focusRequesters.indices) {
                            Modifier.focusRequester(focusRequesters[index])
                        } else {
                            Modifier
                        },
                    ),
                ) {
                    SteamStoreCapsule(
                        app,
                        isFocusedOverride = index == focusIndex,
                        isControllerActive =
                            ControllerHelper
                                .isControllerConnected(),
                        onClick = {
                            selectedAppForDialog =
                                app
                        },
                    )
                }
            }
        }

        if (selectedAppForDialog != null) {
            GameManagerDialog(
                app = selectedAppForDialog!!,
                onDismissRequest = { selectedAppForDialog = null },
            )
        }
    }

    @Composable
    fun SteamStoreCapsule(
        app: SteamApp,
        listMode: Boolean = false,
        isFocusedOverride: Boolean = false,
        isControllerActive: Boolean = false,
        onClick: () -> Unit,
    ) {
        val isInstalled = SteamService.isAppInstalled(app.id)
        val context = LocalContext.current
        var isFocused by remember { mutableStateOf(false) }
        val clickInteraction = remember { MutableInteractionSource() }
        val isPressed by clickInteraction.collectIsPressedAsState()
        val glowAlpha by animateFloatAsState(
            targetValue = if (isPressed) 0.7f else 0f,
            animationSpec = if (isPressed) tween(100) else tween(400),
            label = "steamCapsuleGlow",
        )
        val effectiveFocus = isControllerActive && (isFocusedOverride || isFocused)
        val borderColor = if (isControllerActive) CardBorder else Color.Transparent

        if (listMode) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                        .chasingBorder(isFocused = effectiveFocus, paused = chasingBordersPaused.value, cornerRadius = 14.dp)
                        .background(CardDark, RoundedCornerShape(14.dp))
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .then(
                            if (glowAlpha > 0f) {
                                Modifier.drawWithContent {
                                    drawContent()
                                    drawRoundRect(color = AccentGlow, alpha = glowAlpha * 0.25f, cornerRadius = CornerRadius(14.dp.toPx()))
                                }
                            } else {
                                Modifier
                            },
                        ).clickable(interactionSource = clickInteraction, indication = null, onClick = onClick),
            ) {
                // Hero background
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(app.getHeroUrl())
                            .crossfade(300)
                            .build(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = 0.25f },
                    contentScale = ContentScale.Crop,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .height(52.dp)
                            .aspectRatio(462f / 174f)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(context)
                                    .data(app.getSmallCapsuleUrl())
                                    .crossfade(300)
                                    .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        if (isInstalled) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Installed",
                                tint = StatusOnline,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = app.name,
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(if (effectiveFocus) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .chasingBorder(isFocused = effectiveFocus, paused = chasingBordersPaused.value, cornerRadius = 16.dp)
                        .background(CardDark, RoundedCornerShape(16.dp))
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .then(
                            if (glowAlpha > 0f) {
                                Modifier.drawWithContent {
                                    drawContent()
                                    drawRoundRect(color = AccentGlow, alpha = glowAlpha * 0.25f, cornerRadius = CornerRadius(16.dp.toPx()))
                                }
                            } else {
                                Modifier
                            },
                        ).clickable(interactionSource = clickInteraction, indication = null, onClick = onClick),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                ) {
                    val imageUrl = app.getCapsuleUrl()

                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(context)
                                .data(imageUrl)
                                .crossfade(300)
                                .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    if (isInstalled) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Installed",
                            tint = StatusOnline,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(24.dp),
                        )
                    }
                }

                Text(
                    text = app.name,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .then(if (effectiveFocus) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    // Downloads Tab
    @Composable
    fun DownloadsTab(
        selectedId: String?,
        onSelectDownload: (String?) -> Unit,
    ) {
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

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .tabScreenPadding(top = DownloadsHeaderTopPadding),
        ) {
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val selectedInfo = downloads.find { it.first == selectedId }?.second
                val selectedStatus = selectedInfo?.getStatusFlow()?.value
                val isPaused = selectedStatus == DownloadPhase.PAUSED
                val isComplete = selectedStatus == DownloadPhase.COMPLETE
                val isCancelled = selectedStatus == DownloadPhase.CANCELLED
                val pausableDownloads =
                    downloads.filter {
                        val status = it.second.getStatusFlow().value
                        status != DownloadPhase.COMPLETE && status != DownloadPhase.CANCELLED
                    }
                val allPausableDownloadsPaused =
                    pausableDownloads.isNotEmpty() &&
                        pausableDownloads.all {
                            it.second.getStatusFlow().value == DownloadPhase.PAUSED
                        }

                val pauseResumeLabel =
                    if (selectedId == null) {
                        if (allPausableDownloadsPaused) {
                            stringResource(
                                R.string.downloads_queue_resume_all,
                            )
                        } else {
                            stringResource(R.string.downloads_queue_pause_all)
                        }
                    } else {
                        if (isPaused) stringResource(R.string.session_drawer_resume) else stringResource(R.string.session_drawer_pause)
                    }

                val cancelLabel =
                    if (selectedId ==
                        null
                    ) {
                        getString(R.string.downloads_queue_cancel_all)
                    } else {
                        getString(R.string.common_ui_cancel)
                    }

                // Disable pause/resume for completed or cancelled downloads
                val pauseResumeEnabled =
                    if (selectedId != null) {
                        !isComplete && !isCancelled
                    } else {
                        pausableDownloads.isNotEmpty()
                    }

                val cancelEnabled =
                    if (selectedId != null) {
                        !isComplete && !isCancelled
                    } else {
                        pausableDownloads.isNotEmpty()
                    }

                // Download Queue Size
                var queueSize by remember { mutableIntStateOf(PrefManager.downloadQueueSize) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .height(buttonHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .padding(horizontal = 4.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (queueSize > 1) {
                                queueSize--
                                PrefManager.downloadQueueSize = queueSize
                            }
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = "Decrease Queue",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = queueSize.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                    IconButton(
                        onClick = {
                            queueSize++
                            PrefManager.downloadQueueSize = queueSize
                            com.winlator.cmod.feature.stores.steam.service.SteamService
                                .checkQueue()
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Increase Queue",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
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
                    shape = RoundedCornerShape(8.dp),
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
                    shape = RoundedCornerShape(8.dp),
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
                val hasCompletedOrCancelled =
                    downloads.any {
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
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        stringResource(R.string.downloads_queue_clear),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                        while (kotlin.math.abs(activity.rightStickScrollState.value) > 0.1f) {
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
    fun DownloadItemDeck(
        id: String,
        info: DownloadInfo,
        isSelected: Boolean,
        onClick: () -> Unit,
    ) {
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
        val appId =
            if (isSteam) {
                id.removePrefix("STEAM_").toIntOrNull() ?: 0
            } else if (isEpic) {
                id.removePrefix("EPIC_").toIntOrNull() ?: 0
            } else {
                0
            }
        val gogId = if (isGog) id.removePrefix("GOG_") else ""

        var steamApp by remember(appId) { mutableStateOf<SteamApp?>(null) }
        var epicGame by remember(appId) { mutableStateOf<EpicGame?>(null) }
        var gogGame by remember(gogId) { mutableStateOf<GOGGame?>(null) }
        val context = LocalContext.current
        var isFocused by remember { mutableStateOf(false) }
        val borderColor = if (isFocused || isSelected) Accent.copy(alpha = 0.8f) else Color.Transparent

        LaunchedEffect(appId, gogId, isSteam, isEpic, isGog) {
            withContext(Dispatchers.IO) {
                if (isSteam) {
                    steamApp = db.steamAppDao().findApp(appId)
                } else if (isEpic) {
                    epicGame = EpicService.getEpicGameOf(appId)
                } else if (isGog) {
                    gogGame = GOGService.getGOGGameOf(gogId)
                }
            }
        }

        val unknownGameLabel = stringResource(R.string.library_games_unknown_game)
        val displayName =
            if (isSteam) {
                steamApp?.name
            } else if (isEpic) {
                epicGame?.title
            } else if (isGog) {
                gogGame?.title
            } else {
                unknownGameLabel
            }
        val displayImage =
            if (isSteam) {
                steamApp?.getHeaderImageUrl()
            } else if (isEpic) {
                epicGame?.primaryImageUrl ?: epicGame?.iconUrl
            } else if (isGog) {
                gogGame?.imageUrl ?: gogGame?.iconUrl
            } else {
                null
            }

        Surface(
            color = if (isSelected) SurfaceDark else CardDark,
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(4.dp, borderColor, RoundedCornerShape(12.dp))
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable(onClick = onClick),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(displayImage)
                            .crossfade(300)
                            .build(),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    val currentFile by info.getCurrentFileNameFlow().collectAsState()
                    val (downloadedBytes, totalBytes) = info.getBytesProgress()
                    val speed = info.getCurrentDownloadSpeed() ?: 0L
                    val percentage = (progress * 100).toInt()

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            displayName ?: unknownGameLabel,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Centered Size Info
                        Text(
                            text = "${StorageUtils.formatBinarySize(downloadedBytes)} / ${StorageUtils.formatBinarySize(totalBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            if (status == DownloadPhase.DOWNLOADING && speed > 0) {
                                Text(
                                    text = "${StorageUtils.formatBinarySize(speed)}/s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Accent,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    val statusText =
                        when (status) {
                            DownloadPhase.DOWNLOADING -> {
                                val filePart = currentFile?.let { " [${it.take(10)}]" } ?: ""
                                "Downloading...$filePart"
                            }

                            DownloadPhase.PAUSED -> {
                                stringResource(R.string.downloads_queue_phase_paused)
                            }

                            DownloadPhase.PREPARING -> {
                                "Preparing..."
                            }

                            DownloadPhase.VERIFYING -> {
                                val filePart = currentFile?.let { " [${it.take(10)}]" } ?: ""
                                "Verifying...$filePart"
                            }

                            DownloadPhase.PATCHING -> {
                                "Patching..."
                            }

                            DownloadPhase.COMPLETE -> {
                                stringResource(R.string.downloads_queue_phase_complete)
                            }

                            DownloadPhase.CANCELLED -> {
                                stringResource(R.string.downloads_queue_phase_cancelled)
                            }

                            DownloadPhase.FAILED -> {
                                stringResource(
                                    R.string.downloads_queue_phase_failed,
                                    if (statusMessage != null &&
                                        statusMessage != "null"
                                    ) {
                                        statusMessage!!
                                    } else {
                                        stringResource(R.string.common_ui_unknown_error)
                                    },
                                )
                            }

                            else -> {
                                status.name.lowercase().replaceFirstChar { it.uppercase() }
                            }
                        }

                    Text(
                        "Status: $statusText",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
                            color =
                                when (status) {
                                    DownloadPhase.FAILED -> Color(0xFFFF6B6B)
                                    DownloadPhase.CANCELLED -> Color(0xFFFF6B6B)
                                    DownloadPhase.COMPLETE -> Color(0xFF4CAF50)
                                    else -> Accent
                                },
                            trackColor = Color.Black.copy(alpha = 0.3f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            modifier = Modifier.width(40.dp),
                        )
                    }
                }

                IconButton(
                    onClick = { showDeleteDialog = true },
                    enabled = status != DownloadPhase.COMPLETE && status != DownloadPhase.CANCELLED,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Cancel Download",
                        tint =
                            if (status != DownloadPhase.COMPLETE &&
                                status != DownloadPhase.CANCELLED
                            ) {
                                Color(0xFFFF6B6B)
                            } else {
                                TextSecondary
                            },
                    )
                }
                if (ControllerHelper.isControllerConnected()) {
                    Spacer(Modifier.width(8.dp))
                    ControllerBadge(if (ControllerHelper.isPlayStationController()) "\u2715" else "A")
                }
            }
        }

        if (showDeleteDialog) {
            val gameName =
                if (id.startsWith("STEAM_")) {
                    steamApp?.name
                } else if (id.startsWith("EPIC_")) {
                    epicGame?.title
                } else if (id.startsWith("GOG_")) {
                    gogGame?.title
                } else {
                    null
                }
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = SurfaceDark,
                title = { Text(stringResource(R.string.downloads_queue_cancel_download), color = TextPrimary) },
                text = {
                    Text(
                        "Cancel the download for ${gameName ?: "this game"} and delete all downloaded files?",
                        color = TextSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            DownloadService.cancelDownload(id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B)),
                    ) {
                        Text(stringResource(R.string.downloads_queue_cancel_download), color = Color.White)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showDeleteDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                    ) {
                        Text(stringResource(R.string.common_ui_cancel), color = TextPrimary)
                    }
                },
            )
        }
    }

    // Game Manager Dialog
    @Composable
    fun GameManagerDialog(
        app: SteamApp,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        var isLoading by remember { mutableStateOf(true) }
        var manifestSizes by remember { mutableStateOf(SteamService.ManifestSizes()) }
        var dlcApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        val selectedDlcIds = remember { mutableStateListOf<Int>() }
        var customPath by remember { mutableStateOf<String?>(null) }
        var showCustomPathWarning by remember { mutableStateOf(false) }
        var showDlcDialog by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val folderPickerLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri -> uri?.let { customPath = getPathFromTreeUri(it) } }

        if (showCustomPathWarning) {
            CustomPathWarningDialog(
                onDismiss = { showCustomPathWarning = false },
                onProceed = {
                    showCustomPathWarning = false
                    folderPickerLauncher.launch(null)
                },
            )
        }

        if (showDlcDialog && dlcApps.isNotEmpty()) {
            GameSettingsDialogFrame(
                title = stringResource(R.string.library_games_dlcs),
                onDismissRequest = { showDlcDialog = false },
            ) {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    dlcApps.forEachIndexed { index, dlc ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = CardBorder.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        if (selectedDlcIds.contains(dlc.id)) {
                                            selectedDlcIds.remove(dlc.id)
                                        } else {
                                            selectedDlcIds.add(dlc.id)
                                        }
                                    }.padding(horizontal = 16.dp, vertical = 2.dp),
                        ) {
                            Checkbox(
                                checked = selectedDlcIds.contains(dlc.id),
                                onCheckedChange = { if (it) selectedDlcIds.add(dlc.id) else selectedDlcIds.remove(dlc.id) },
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = Accent,
                                        uncheckedColor = TextSecondary,
                                        checkmarkColor = Color.White,
                                    ),
                            )
                            Text(dlc.name, color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        val selectedDlcIdsKey = selectedDlcIds.toList().sorted().joinToString(",")

        LaunchedEffect(app.id) {
            val (downloadableDlcApps, sizes) =
                withContext(Dispatchers.IO) {
                    (db.steamAppDao().findDownloadableDLCApps(app.id) ?: emptyList<SteamApp>()) to
                        SteamService.getSelectedManifestSizes(app.id)
                }
            dlcApps = downloadableDlcApps
            manifestSizes = sizes
            isLoading = false
        }

        LaunchedEffect(app.id, selectedDlcIdsKey) {
            if (isLoading) return@LaunchedEffect
            manifestSizes =
                withContext(Dispatchers.IO) {
                    SteamService.getSelectedManifestSizes(app.id, selectedDlcIds.toList())
                }
        }

        val totalInstallSize = manifestSizes.installSize
        val totalDownloadSize = manifestSizes.downloadSize
        val defaultPathSet =
            if (PrefManager.useSingleDownloadFolder) {
                PrefManager.defaultDownloadFolder.isNotEmpty()
            } else {
                PrefManager.steamDownloadFolder
                    .isNotEmpty()
            }
        val effectivePath = customPath ?: SteamService.defaultAppInstallPath
        val availableBytes =
            try {
                StorageUtils.getAvailableSpace(effectivePath)
            } catch (e: Exception) {
                0L
            }
        val isInstallEnabled = availableBytes >= totalInstallSize
        val installPathDisplay = customPath ?: SteamService.defaultAppInstallPath
        val installed = SteamService.isAppInstalled(app.id)

        StoreInstallDialogShell(
            title = app.name,
            heroImageUrl = app.getHeroUrl(),
            subtitle =
                listOfNotNull(
                    app.developer.takeIf { it.isNotBlank() },
                    app.publisher.takeIf { it.isNotBlank() },
                ).joinToString(" • "),
            sourceLabel = "Steam",
            onDismissRequest = onDismissRequest,
            infoContent = {
                if (isLoading) {
                    Spacer(Modifier.height(18.dp))
                    CircularProgressIndicator(color = Accent)
                } else {
                    DetailCard(
                        label = stringResource(R.string.library_games_install_path),
                        value = installPathDisplay,
                    )
                    DetailCard(
                        stringResource(R.string.library_games_download_slash_install),
                        stringResource(
                            R.string.library_games_download_install_available,
                            StorageUtils.formatBinarySize(totalDownloadSize),
                            StorageUtils.formatBinarySize(totalInstallSize),
                            StorageUtils.formatBinarySize(availableBytes),
                        ),
                        valueColor = if (!isInstallEnabled) DangerRed else null,
                    )
                }
            },
        ) {
            if (!installed) {
                InstallButton(
                    loading = isLoading,
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            SteamService.downloadApp(app.id, selectedDlcIds.toList(), false, customPath)
                            withContext(Dispatchers.Main) { onDismissRequest() }
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactActionButton(
                    icon = Icons.Outlined.Folder,
                    label =
                        if (customPath !=
                            null
                        ) {
                            stringResource(R.string.common_ui_custom)
                        } else if (defaultPathSet) {
                            stringResource(R.string.common_ui_already_set)
                        } else {
                            stringResource(R.string.common_ui_custom)
                        },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (customPath == null && defaultPathSet) {
                            showCustomPathWarning = true
                        } else {
                            folderPickerLauncher.launch(null)
                        }
                    },
                )
                if (dlcApps.isNotEmpty()) {
                    CompactActionButton(
                        icon = Icons.Outlined.Extension,
                        label = stringResource(R.string.library_games_dlcs),
                        modifier = Modifier.weight(1f),
                        onClick = { showDlcDialog = true },
                    )
                }
            }
        }
    }

    private fun getPathFromTreeUri(uri: Uri): String? =
        try {
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
                } else {
                    null
                }
            } else {
                docId
            }
        } catch (e: Exception) {
            uri.path
        }

    private fun findLibraryShortcutForGame(
        containerManager: ContainerManager,
        app: SteamApp,
        isCustom: Boolean,
        isEpic: Boolean,
        epicId: Int,
    ): Shortcut? = findShortcutForGame(containerManager.loadShortcuts(), app, isCustom, isEpic, epicId)

    private fun findShortcutForGame(
        shortcuts: List<Shortcut>,
        app: SteamApp,
        isCustom: Boolean,
        isEpic: Boolean,
        epicId: Int,
    ): Shortcut? =
        when {
            isEpic -> {
                shortcuts.find {
                    it.getExtra("game_source") == "EPIC" && it.getExtra("app_id") == epicId.toString()
                }
            }

            else -> {
                shortcuts.find {
                    it.getExtra("app_id") == app.id.toString() || it.getExtra("custom_name") == app.name || it.name == app.name
                }
            }
        }

    private fun isShortcutCloudSyncEnabled(shortcut: Shortcut?): Boolean =
        shortcut == null || shortcut.getExtra("cloud_sync_disabled", "0") != "1"

    private fun setShortcutCloudSyncEnabled(
        shortcut: Shortcut?,
        enabled: Boolean,
    ) {
        if (shortcut == null) return
        shortcut.putExtra("cloud_sync_disabled", if (enabled) null else "1")
        if (enabled) {
            shortcut.putExtra("cloud_force_download", null)
        }
        shortcut.saveData()
    }

    @Composable
    private fun CloudSavesContent(
        isWorking: Boolean,
        cloudSyncEnabled: Boolean,
        onCloudSyncToggle: (Boolean) -> Unit,
        onBackup: () -> Unit,
        onRestore: () -> Unit,
        onBack: () -> Unit,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.cloud_saves_title),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.cloud_sync_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(
                                if (cloudSyncEnabled) {
                                    R.string.cloud_sync_enabled_summary
                                } else {
                                    R.string.cloud_sync_disabled_summary
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Switch(
                        checked = cloudSyncEnabled,
                        onCheckedChange = onCloudSyncToggle,
                        colors =
                            outlinedSwitchColors(
                                accentColor = Accent,
                                textSecondaryColor = TextSecondary,
                                checkedThumbColor = TextPrimary,
                            ),
                    )
                }
            }

            if (isWorking) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = CardBorder,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CompactActionButton(
                    icon = Icons.Outlined.CloudUpload,
                    label = stringResource(R.string.cloud_saves_backup),
                    modifier = Modifier.weight(1f),
                    onClick = onBackup,
                )
                CompactActionButton(
                    icon = Icons.Outlined.CloudDownload,
                    label = stringResource(R.string.cloud_saves_restore),
                    modifier = Modifier.weight(1f),
                    onClick = onRestore,
                )
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.common_ui_back), color = TextSecondary)
            }
        }
    }

    private fun resolveLibraryShortcutArtworkModel(
        context: android.content.Context,
        app: SteamApp,
        isCustom: Boolean,
        isEpic: Boolean,
        epicArtworkUrl: String?,
    ): Any? =
        when {
            isCustom -> {
                val safeName = app.name.replace("/", "_").replace("\\", "_")
                val iconFile = java.io.File(context.filesDir, "custom_icons/$safeName.png")
                if (iconFile.exists()) iconFile else null
            }

            isEpic -> {
                epicArtworkUrl?.takeIf { it.isNotBlank() }
            }

            else -> {
                app.getCapsuleUrl()
            }
        }

    private suspend fun loadArtworkBitmap(
        context: android.content.Context,
        artworkModel: Any?,
    ): Bitmap? {
        if (artworkModel == null) return null
        return try {
            val request =
                ImageRequest
                    .Builder(context)
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
        artworkModel: Any? = null,
    ): Boolean {
        if (shortcut.getExtra("uuid").isEmpty()) {
            shortcut.genUUID()
        }
        val shortcutId = shortcut.getExtra("uuid")
        if (shortcutId.isEmpty()) return false
        val canonicalShortcutPath = shortcut.file.absolutePath
        val shortcutPathHash = canonicalShortcutPath.hashCode()
        val containerIdForLaunch = shortcut.getExtra("container_id").toIntOrNull() ?: shortcut.container.id
        val pinShortcutId = "shortcut_${shortcut.container.id}_${shortcutId}_${shortcutPathHash.toUInt().toString(16)}"

        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java) ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) return false

        val launchIntent =
            Intent(context, XServerDisplayActivity::class.java).apply {
                val launchData =
                    Uri
                        .Builder()
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

        val customIconPath =
            shortcut
                .getExtra("customLibraryIconPath")
                .ifBlank { shortcut.getExtra("customCoverArtPath") }
        val customArtworkModel =
            customIconPath
                .takeIf { it.isNotBlank() }
                ?.let { java.io.File(it) }
                ?.takeIf { it.exists() }

        val artworkBitmap = loadArtworkBitmap(context, customArtworkModel) ?: loadArtworkBitmap(context, artworkModel)
        val shortcutIcon =
            artworkBitmap?.let {
                android.graphics.drawable.Icon
                    .createWithBitmap(it)
            }
                ?: shortcut.icon?.let {
                    android.graphics.drawable.Icon
                        .createWithBitmap(it)
                }
                ?: android.graphics.drawable.Icon
                    .createWithResource(context, R.drawable.icon_shortcut)

        val pinShortcutInfo =
            android.content.pm.ShortcutInfo
                .Builder(context, pinShortcutId)
                .setShortLabel(shortcut.name)
                .setLongLabel(shortcut.name)
                .setIcon(shortcutIcon)
                .setIntent(launchIntent)
                .build()

        val callbackIntent =
            Intent(context, ShortcutBroadcastReceiver::class.java).apply {
                action = ShortcutBroadcastReceiver.ACTION_PIN_SHORTCUT_RESULT
                putExtra("shortcut_path", canonicalShortcutPath)
                putExtra("shortcut_name", shortcut.name)
            }
        val callbackFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val callback =
            PendingIntent.getBroadcast(
                context,
                pinShortcutId.hashCode(),
                callbackIntent,
                callbackFlags,
            )

        val result =
            ShortcutsFragment.pinOrUpdateShortcut(
                shortcutManager,
                pinShortcutInfo,
                ShortcutsFragment.buildPinnedShortcutIds(containerIdForLaunch, shortcutId, canonicalShortcutPath),
                callback.intentSender,
            )
        if (result == ShortcutsFragment.PinShortcutResult.REUSED_EXISTING) {
            val toastIcon = artworkBitmap ?: shortcut.icon
            com.winlator.cmod.shared.android.AppUtils.showToast(
                context,
                R.string.shortcuts_list_readded_existing,
                toastIcon,
            )
        }
        return result != ShortcutsFragment.PinShortcutResult.FAILED
    }

    private suspend fun addLibraryShortcutToHomeScreen(
        context: android.content.Context,
        app: SteamApp,
        isCustom: Boolean,
        isEpic: Boolean,
        epicId: Int,
        epicArtworkUrl: String? = null,
    ): Boolean {
        val containerManager = ContainerManager(context)
        val shortcut = findLibraryShortcutForGame(containerManager, app, isCustom, isEpic, epicId) ?: return false
        val artworkModel = resolveLibraryShortcutArtworkModel(context, app, isCustom, isEpic, epicArtworkUrl)
        return requestPinnedHomeShortcut(context, shortcut, artworkModel)
    }

    private suspend fun addGogShortcutToHomeScreen(
        context: android.content.Context,
        app: GOGGame,
        artworkUrl: String?,
    ): Boolean {
        val shortcut =
            ContainerManager(context).loadShortcuts().find {
                it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == app.id
            } ?: return false
        val artworkModel = artworkUrl?.takeIf { it.isNotBlank() }
        return requestPinnedHomeShortcut(context, shortcut, artworkModel)
    }

    // Game launch with drive-aware mapping
    private fun launchSteamGame(
        context: android.content.Context,
        containerManager: ContainerManager,
        app: SteamApp,
    ) {
        val gameInstallPath = SteamService.getAppDirPath(app.id)
        val gameDir = java.io.File(gameInstallPath)
        if (!gameDir.exists()) {
            com.winlator.cmod.shared.android.AppUtils.showToast(
                context,
                "Game not installed: ${app.name}",
                android.widget.Toast.LENGTH_SHORT,
            )
            return
        }

        val shortcut =
            containerManager.loadShortcuts().find {
                it.getExtra("app_id") == app.id.toString()
            }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val launchExecutable =
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    SteamService.getInstalledExe(app.id)
                }

            if (shortcut != null) {
                if (!SetupWizardActivity.isContainerUsable(context, shortcut.container)) {
                    SetupWizardActivity.promptToInstallWineOrCreateContainer(
                        context,
                        shortcut.container.wineVersion,
                    )
                    return@launch
                }
                ensureGameDrive(shortcut.container, gameInstallPath)
                shortcut.putExtra("game_source", "STEAM")
                shortcut.putExtra("game_install_path", gameInstallPath)
                shortcut.putExtra("launch_exe_path", launchExecutable)
                val loaderExec = "wine \"C:\\\\Program Files (x86)\\\\Steam\\\\steamclient_loader_x64.exe\""
                val lines =
                    com.winlator.cmod.shared.io.FileUtils
                        .readLines(shortcut.file)
                val rewritten = StringBuilder()
                var execUpdated = false
                for (line in lines) {
                    if (line.startsWith("Exec=")) {
                        rewritten.append("Exec=").append(loaderExec).append("\n")
                        execUpdated = true
                    } else {
                        rewritten.append(line).append("\n")
                    }
                }
                if (!execUpdated) {
                    rewritten.append("Exec=").append(loaderExec).append("\n")
                }
                com.winlator.cmod.shared.io.FileUtils
                    .writeString(shortcut.file, rewritten.toString())
                shortcut.saveData()
                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", shortcut.container.id)
                intent.putExtra("shortcut_path", shortcut.file.path)
                intent.putExtra("shortcut_name", shortcut.name)
                launchGame(context, intent)
            } else {
                val container = SetupWizardActivity.getPreferredGameContainer(context, containerManager)

                if (container == null) {
                    SetupWizardActivity.promptToInstallWineOrCreateContainer(context)
                    return@launch
                }

                ensureGameDrive(container, gameInstallPath)

                val execPath = "wine \"C:\\\\Program Files (x86)\\\\Steam\\\\steamclient_loader_x64.exe\""

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
                content.append("launch_exe_path=${launchExecutable}\n")
                content.append("use_container_defaults=1\n")

                com.winlator.cmod.shared.io.FileUtils
                    .writeString(shortcutFile, content.toString())

                container.saveData()

                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", container.id)
                intent.putExtra("shortcut_path", shortcutFile.path)
                intent.putExtra("shortcut_name", app.name)
                launchGame(context, intent)
            }
        }
    }

    private fun launchEpicGame(
        context: android.content.Context,
        containerManager: ContainerManager,
        app: EpicGame,
    ) {
        val gameInstallPath = app.installPath.takeIf { it.isNotEmpty() } ?: EpicConstants.getGameInstallPath(context, app.appName)
        val gameDir = java.io.File(gameInstallPath)
        if (!gameDir.exists()) {
            com.winlator.cmod.shared.android.AppUtils.showToast(
                context,
                "Game not installed: ${app.title}",
                android.widget.Toast.LENGTH_SHORT,
            )
            return
        }

        // Try to find an existing shortcut first (preserves per-game settings)
        var existingShortcut =
            containerManager.loadShortcuts().find {
                it.getExtra("game_source") == "EPIC" && it.getExtra("app_id") == app.id.toString()
            }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val launchArgsResult =
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    EpicGameLauncher.buildLaunchParameters(context, app)
                }
            val args = launchArgsResult.getOrNull()?.joinToString(" ") ?: ""

            if (existingShortcut != null) {
                if (!SetupWizardActivity.isContainerUsable(context, existingShortcut!!.container)) {
                    SetupWizardActivity.promptToInstallWineOrCreateContainer(
                        context,
                        existingShortcut!!.container.wineVersion,
                    )
                    return@launch
                }
                // Existing shortcut found: preserve per-game settings and update the mapped install path
                val shortcut = existingShortcut!!
                // Ensure game_install_path is always up-to-date
                shortcut.putExtra("game_install_path", gameInstallPath)
                ensureGameDrive(shortcut.container, gameInstallPath)

                // Repair broken Exec line if the executable is missing or still points at a legacy placeholder mapping.
                val currentPath = shortcut.path
                if (currentPath == null || currentPath == "D:\\" || currentPath == "D:\\\\" ||
                    currentPath == "A:\\" || currentPath == "A:\\\\" ||
                    currentPath.startsWith("A:\\")
                ) {
                    var exePath = withContext(kotlinx.coroutines.Dispatchers.IO) { EpicService.getInstalledExe(app.id) }
                    val newExecCmd =
                        if (exePath.isNotEmpty()) {
                            buildStoreWineExecCommand(
                                shortcut.container,
                                "EPIC",
                                gameInstallPath,
                                java.io.File(gameInstallPath, exePath.replace("\\", "/")),
                            )
                        } else {
                            val exeFile = findGameExe(gameDir)
                            if (exeFile != null) {
                                buildStoreWineExecCommand(shortcut.container, "EPIC", gameInstallPath, exeFile)
                            } else {
                                null
                            }
                        }
                    if (newExecCmd != null) {
                        // Rewrite the Exec line in the .desktop file while preserving all other content
                        val lines =
                            com.winlator.cmod.shared.io.FileUtils
                                .readLines(shortcut.file)
                        val sb = StringBuilder()
                        for (line in lines) {
                            if (line.startsWith("Exec=")) {
                                sb.append("Exec=$newExecCmd\n")
                            } else {
                                sb.append(line).append("\n")
                            }
                        }
                        com.winlator.cmod.shared.io.FileUtils
                            .writeString(shortcut.file, sb.toString())
                    }
                }

                shortcut.saveData()
                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", shortcut.container.id)
                intent.putExtra("shortcut_path", shortcut.file.path)
                intent.putExtra("shortcut_name", shortcut.name)
                intent.putExtra("extra_exec_args", args) // Pass fresh tokens
                launchGame(context, intent)
            } else {
                // No existing shortcut — create a new one
                var exePath = withContext(kotlinx.coroutines.Dispatchers.IO) { EpicService.getInstalledExe(app.id) }

                var container = SetupWizardActivity.getPreferredGameContainer(context, containerManager)

                if (container == null) {
                    SetupWizardActivity.promptToInstallWineOrCreateContainer(context)
                    return@launch
                }

                ensureGameDrive(container, gameInstallPath)
                val execCmd =
                    if (exePath.isNotEmpty()) {
                        buildStoreWineExecCommand(
                            container,
                            "EPIC",
                            gameInstallPath,
                            java.io.File(gameInstallPath, exePath.replace("\\", "/")),
                        )
                    } else {
                        val exeFile = findGameExe(gameDir)
                        if (exeFile != null) {
                            buildStoreWineExecCommand(container, "EPIC", gameInstallPath, exeFile)
                        } else {
                            "wine \"explorer.exe\""
                        }
                    }

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
                content.append("use_container_defaults=1\n")

                com.winlator.cmod.shared.io.FileUtils
                    .writeString(shortcutFile, content.toString())

                container.saveData()

                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", container.id)
                intent.putExtra("shortcut_path", shortcutFile.path)
                intent.putExtra("shortcut_name", app.title)
                intent.putExtra("extra_exec_args", args) // Pass fresh tokens
                launchGame(context, intent)
            }
        }
    }

    private fun launchGogGame(
        context: android.content.Context,
        containerManager: ContainerManager,
        app: GOGGame,
    ) {
        val gameInstallPath = app.installPath.takeIf { it.isNotEmpty() } ?: GOGConstants.getGameInstallPath(app.title)
        val gameDir = java.io.File(gameInstallPath)
        if (!gameDir.exists()) {
            com.winlator.cmod.shared.android.AppUtils.showToast(
                context,
                "Game not installed: ${app.title}",
                android.widget.Toast.LENGTH_SHORT,
            )
            return
        }

        var existingShortcut =
            containerManager.loadShortcuts().find {
                it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == app.id
            }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val gogAppId = "GOG_${app.id}"
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                GOGService.syncCloudSaves(context, gogAppId)
            }

            if (existingShortcut != null) {
                val shortcut = existingShortcut!!
                if (!SetupWizardActivity.isContainerUsable(context, shortcut.container)) {
                    SetupWizardActivity.promptToInstallWineOrCreateContainer(
                        context,
                        shortcut.container.wineVersion,
                    )
                    return@launch
                }
                shortcut.putExtra("game_install_path", gameInstallPath)
                ensureGameDrive(shortcut.container, gameInstallPath)

                // Repair broken Exec line if the executable is missing or still points at a legacy placeholder mapping.
                val currentPath = shortcut.path
                if (currentPath == null || currentPath == "D:\\" || currentPath == "D:\\\\" ||
                    currentPath == "A:\\" || currentPath == "A:\\\\" ||
                    currentPath.startsWith("A:\\")
                ) {
                    val newExecCmd =
                        if (shortcut.getExtra("launch_exe_path").isNotEmpty()) {
                            val selectedExe = java.io.File(shortcut.getExtra("launch_exe_path"))
                            if (selectedExe.exists()) {
                                val normalizedBaseDir =
                                    java.io
                                        .File(gameInstallPath)
                                        .absolutePath
                                        .removeSuffix("/")
                                val normalizedExePath = selectedExe.absolutePath
                                if (normalizedExePath == normalizedBaseDir || normalizedExePath.startsWith("$normalizedBaseDir/")) {
                                    buildStoreWineExecCommand(shortcut.container, "GOG", gameInstallPath, selectedExe)
                                } else {
                                    val hostPath = normalizedExePath.replace("/", "\\\\").let { if (it.startsWith("\\")) it else "\\$it" }
                                    "wine \"Z:${hostPath}\""
                                }
                            } else {
                                null
                            }
                        } else {
                            val libraryItem =
                                LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.feature.stores.steam.enums.GameSource.GOG)
                            val exePath =
                                withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    GOGService.getInstalledExe(libraryItem)
                                }
                            if (exePath.isNotEmpty()) {
                                buildStoreWineExecCommand(
                                    shortcut.container,
                                    "GOG",
                                    gameInstallPath,
                                    java.io.File(gameInstallPath, exePath.replace("\\", "/")),
                                )
                            } else {
                                val exeFile = findGameExe(gameDir)
                                if (exeFile != null) {
                                    buildStoreWineExecCommand(shortcut.container, "GOG", gameInstallPath, exeFile)
                                } else {
                                    null
                                }
                            }
                        }
                    if (newExecCmd != null) {
                        val lines =
                            com.winlator.cmod.shared.io.FileUtils
                                .readLines(shortcut.file)
                        val sb = StringBuilder()
                        for (line in lines) {
                            if (line.startsWith("Exec=")) {
                                sb.append("Exec=$newExecCmd\n")
                            } else {
                                sb.append(line).append("\n")
                            }
                        }
                        com.winlator.cmod.shared.io.FileUtils
                            .writeString(shortcut.file, sb.toString())
                    }
                }

                shortcut.saveData()

                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", shortcut.container.id)
                intent.putExtra("shortcut_path", shortcut.file.path)
                intent.putExtra("shortcut_name", shortcut.name)
                launchGame(context, intent)
                return@launch
            }

            val libraryItem = LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.feature.stores.steam.enums.GameSource.GOG)
            val exePath =
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    GOGService.getInstalledExe(libraryItem)
                }

            var container = SetupWizardActivity.getPreferredGameContainer(context, containerManager)

            if (container == null) {
                SetupWizardActivity.promptToInstallWineOrCreateContainer(context)
                return@launch
            }

            ensureGameDrive(container, gameInstallPath)
            val execCmd =
                if (exePath.isNotEmpty()) {
                    buildStoreWineExecCommand(
                        container,
                        "GOG",
                        gameInstallPath,
                        java.io.File(gameInstallPath, exePath.replace("\\", "/")),
                    )
                } else {
                    val exeFile = findGameExe(gameDir)
                    if (exeFile != null) {
                        buildStoreWineExecCommand(container, "GOG", gameInstallPath, exeFile)
                    } else {
                        "wine \"explorer.exe\""
                    }
                }

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
            content.append("use_container_defaults=1\n")

            com.winlator.cmod.shared.io.FileUtils
                .writeString(shortcutFile, content.toString())
            container.saveData()

            val intent = Intent(context, XServerDisplayActivity::class.java)
            intent.putExtra("container_id", container.id)
            intent.putExtra("shortcut_path", shortcutFile.path)
            intent.putExtra("shortcut_name", app.title)
            launchGame(context, intent)
        }
    }

    private fun ensureGameDrive(
        container: com.winlator.cmod.runtime.container.Container,
        gamePath: String,
        preferredLetter: String = "F",
    ) {
        container.drives =
            com.winlator.cmod.runtime.wine.WineUtils.normalizePersistentDrives(
                this,
                container.drives ?: com.winlator.cmod.runtime.container.Container.DEFAULT_DRIVES,
            )
    }

    private fun buildWineExecCommand(
        container: com.winlator.cmod.runtime.container.Container?,
        gameInstallPath: String,
        relativeExePath: String,
    ): String {
        val exeFile = java.io.File(gameInstallPath, relativeExePath.replace("\\", "/"))
        return buildWineExecCommand(container, gameInstallPath, exeFile)
    }

    private fun buildWineExecCommand(
        container: com.winlator.cmod.runtime.container.Container?,
        gameInstallPath: String,
        exeFile: java.io.File,
    ): String {
        val windowsPath =
            container?.let {
                com.winlator.cmod.runtime.wine.WineUtils
                    .getWindowsPath(it, exeFile.absolutePath)
            } ?: run {
                val relativePath =
                    try {
                        exeFile.relativeTo(java.io.File(gameInstallPath)).path.replace("/", "\\")
                    } catch (_: Exception) {
                        exeFile.name
                    }
                "F:\\$relativePath"
            }
        return "wine \"$windowsPath\""
    }

    private fun buildStoreWineExecCommand(
        container: com.winlator.cmod.runtime.container.Container?,
        source: String,
        gameInstallPath: String,
        exeFile: java.io.File,
    ): String {
        val windowsPath =
            container?.let {
                com.winlator.cmod.runtime.wine.WineUtils.getDriveCGameWindowsPath(
                    it,
                    source,
                    gameInstallPath,
                    exeFile.absolutePath,
                )
            } ?: run {
                val relativePath =
                    try {
                        exeFile.relativeTo(java.io.File(gameInstallPath)).path.replace("/", "\\")
                    } catch (_: Exception) {
                        exeFile.name
                    }
                "C:\\WinNative\\Games\\$source\\${java.io.File(gameInstallPath).name}\\$relativePath"
            }
        return "wine \"$windowsPath\""
    }

    // Launch custom game by shortcut name
    private fun launchCustomGame(
        context: android.content.Context,
        containerManager: ContainerManager,
        gameName: String,
    ) {
        val allShortcuts = containerManager.loadShortcuts()

        // Try matching by app_id (for non-official Steam/Epic), custom_name, or filename
        var shortcut =
            allShortcuts.find { it.getExtra("app_id") == gameName }
                ?: allShortcuts.find { it.getExtra("custom_name") == gameName }
                ?: allShortcuts.find { it.name == gameName }
                ?: allShortcuts.find { it.name == gameName.replace("/", "_").replace("\\", "_") }

        // If still not found, try matching by looking at the safe filename directly
        if (shortcut == null) {
            val safeName = gameName.replace("/", "_").replace("\\", "_")
            for (container in containerManager.containers) {
                val desktopFile = java.io.File(container.getDesktopDir(), "$safeName.desktop")
                if (desktopFile.exists()) {
                    shortcut =
                        com.winlator.cmod.runtime.container
                            .Shortcut(container, desktopFile)
                    break
                }
            }
        }

        if (shortcut == null) {
            com.winlator.cmod.shared.android.AppUtils.showToast(
                context,
                "Custom game shortcut not found: $gameName",
                android.widget.Toast.LENGTH_SHORT,
            )
            return
        }

        // Backfill custom_name if missing (legacy shortcuts)
        if (shortcut.getExtra("custom_name").isEmpty()) {
            shortcut.putExtra("custom_name", gameName)
            shortcut.saveData()
        }

        // Ensure the custom game folder is mapped into the container.
        val gameFolder = shortcut.getExtra("custom_game_folder", "")
        if (gameFolder.isNotEmpty()) {
            ensureGameDrive(shortcut.container, gameFolder, "F")
            shortcut.container.saveData()
        }
        val intent = Intent(context, XServerDisplayActivity::class.java)
        intent.putExtra("container_id", shortcut.container.id)
        intent.putExtra("shortcut_path", shortcut.file.path)
        intent.putExtra("shortcut_name", gameName)
        launchGame(context, intent)
    }

    private fun launchGame(
        context: android.content.Context,
        intent: Intent,
    ) {
        context.startActivity(intent)
        // Suppress the default activity transition so the preloader stays seamless
        if (context is android.app.Activity) {
            com.winlator.cmod.shared.android.AppUtils
                .applyOpenActivityTransition(context, 0, 0)
        }
    }

    private fun findGameExe(dir: java.io.File): java.io.File? {
        // BFS: check each directory level fully before going deeper
        val exclusions =
            listOf(
                "unins",
                "redist",
                "setup",
                "dotnet",
                "vcredist",
                "dxsetup",
                "helper",
                "crash",
                "ue4prereq",
                "dxwebsetup",
                "launcher",
            )

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
            val exe64 =
                candidates.find {
                    it.name.lowercase().contains("64") ||
                        it.parentFile
                            ?.name
                            ?.lowercase()
                            ?.contains("64") == true
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
    fun LoginRequiredScreen(
        storeName: String,
        onLoginClick: () -> Unit,
    ) {
        val message =
            if (storeName ==
                "Library"
            ) {
                stringResource(R.string.library_games_sign_in_prompt)
            } else {
                stringResource(R.string.stores_accounts_sign_in_store_prompt, storeName)
            }
        val buttonText =
            if (storeName ==
                "Library"
            ) {
                stringResource(R.string.stores_accounts_manage)
            } else {
                stringResource(R.string.stores_accounts_sign_into_store, storeName)
            }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 48.dp),
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    message,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(20.dp))
                val interactionSource =
                    remember {
                        androidx.compose.foundation.interaction
                            .MutableInteractionSource()
                    }
                val isPressed by interactionSource.collectIsPressedAsState()
                val btnScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = tween(100),
                    label = "btnScale",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .graphicsLayer {
                                scaleX = btnScale
                                scaleY = btnScale
                            }.clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onLoginClick,
                            ).border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(buttonText, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // Drawer content: avatar card + filters
    @Composable
    private fun DrawerContent(
        persona: com.winlator.cmod.feature.stores.steam.data.SteamFriend?,
        context: android.content.Context,
        scope: kotlinx.coroutines.CoroutineScope,
        storeVisible: SnapshotStateMap<String, Boolean>,
        contentFilters: SnapshotStateMap<String, Boolean>,
        libraryLayoutMode: LibraryLayoutMode,
        onLibraryLayoutSelected: (LibraryLayoutMode) -> Unit,
        onClose: () -> Unit,
    ) {
        val currentState = persona?.state ?: EPersonaState.Online
        var statusExpanded by remember { mutableStateOf(false) }

        ModalDrawerSheet(
            drawerShape = RectangleShape,
            drawerContainerColor = BgDark,
            drawerContentColor = TextPrimary,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.width(324.dp),
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                // ── Avatar Card ──
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { statusExpanded = !statusExpanded },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val avatarUrl =
                                persona?.avatarHash?.getAvatarURL()
                                    ?: "https://steamcdn-a.akamaihd.net/steamcommunity/public/images/avatars/fe/fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb_full.jpg"

                            Box(
                                modifier =
                                    Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                            ) {
                                AsyncImage(
                                    model =
                                        ImageRequest
                                            .Builder(context)
                                            .data(avatarUrl)
                                            .crossfade(true)
                                            .build(),
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = persona?.name ?: stringResource(R.string.stores_accounts_not_signed_in),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val statusLabel =
                                    when (currentState) {
                                        EPersonaState.Online -> stringResource(R.string.stores_accounts_status_online)
                                        EPersonaState.Away -> stringResource(R.string.stores_accounts_status_away)
                                        else -> stringResource(R.string.stores_accounts_status_offline)
                                    }
                                val statusColor =
                                    when (currentState) {
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
                                label = "chevronRotation",
                            )
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = "Toggle status",
                                tint = TextSecondary,
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .graphicsLayer { rotationZ = chevronRotation },
                            )
                        }

                        // Expandable status options
                        AnimatedVisibility(visible = statusExpanded) {
                            Column(Modifier.padding(top = 12.dp)) {
                                HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.stores_accounts_status_header),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                                Spacer(Modifier.height(8.dp))

                                listOf(
                                    Triple(EPersonaState.Online, stringResource(R.string.stores_accounts_status_online), StatusOnline),
                                    Triple(EPersonaState.Away, stringResource(R.string.stores_accounts_status_away), StatusAway),
                                    Triple(
                                        EPersonaState.Invisible,
                                        stringResource(R.string.stores_accounts_status_invisible),
                                        StatusOffline,
                                    ),
                                ).forEach { (state, label, color) ->
                                    val isSelected = currentState == state
                                    val rowBg by animateColorAsState(
                                        targetValue = if (isSelected) Accent.copy(alpha = 0.12f) else Color.Transparent,
                                        animationSpec = tween(250),
                                        label = "statusRowBg",
                                    )
                                    val borderAlpha by animateFloatAsState(
                                        targetValue = if (isSelected) 1f else 0f,
                                        animationSpec = tween(250),
                                        label = "statusBorder",
                                    )
                                    val checkScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1f else 0f,
                                        animationSpec =
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                        label = "checkScale",
                                    )
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(rowBg)
                                                .border(1.dp, Accent.copy(alpha = 0.4f * borderAlpha), RoundedCornerShape(8.dp))
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    scope.launch {
                                                        SteamService.setPersonaState(state)
                                                        statusExpanded = false
                                                    }
                                                }.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(Modifier.size(10.dp).background(color, CircleShape))
                                        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.weight(1f))
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = Accent,
                                            modifier =
                                                Modifier
                                                    .size(16.dp)
                                                    .graphicsLayer {
                                                        scaleX = checkScale
                                                        scaleY = checkScale
                                                        alpha = checkScale
                                                    },
                                        )
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
                Text(
                    stringResource(R.string.library_games_layouts_header),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton(
                        label = "4-Grid",
                        checked = libraryLayoutMode == LibraryLayoutMode.GRID_4,
                        modifier = Modifier.weight(1f),
                    ) { if (it) onLibraryLayoutSelected(LibraryLayoutMode.GRID_4) }
                    DrawerFilterButton(
                        label = stringResource(R.string.library_games_layout_carousel),
                        checked = libraryLayoutMode == LibraryLayoutMode.CAROUSEL,
                        modifier = Modifier.weight(1f),
                    ) { if (it) onLibraryLayoutSelected(LibraryLayoutMode.CAROUSEL) }
                    DrawerFilterButton(
                        label = stringResource(R.string.library_games_layout_list),
                        checked = libraryLayoutMode == LibraryLayoutMode.LIST,
                        modifier = Modifier.weight(1f),
                    ) { if (it) onLibraryLayoutSelected(LibraryLayoutMode.LIST) }
                }

                Spacer(Modifier.height(16.dp))

                // ── Stores ──
                Text(
                    stringResource(R.string.stores_accounts_stores_header),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton("Steam", storeVisible["steam"] == true, Modifier.weight(1f)) { storeVisible["steam"] = it }
                    DrawerFilterButton("Epic", storeVisible["epic"] == true, Modifier.weight(1f)) { storeVisible["epic"] = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton("GOG", storeVisible["gog"] == true, Modifier.weight(1f)) { storeVisible["gog"] = it }
                    Spacer(Modifier.weight(1f))
                }

                Spacer(Modifier.height(16.dp))

                // ── Content Types ──
                Text(
                    stringResource(R.string.settings_content_types_header),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton("Games", contentFilters["games"] == true, Modifier.weight(1f)) { contentFilters["games"] = it }
                    DrawerFilterButton("DLC", contentFilters["dlc"] == true, Modifier.weight(1f)) { contentFilters["dlc"] = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton("Applications", contentFilters["applications"] == true, Modifier.weight(1f)) {
                        contentFilters["applications"] =
                            it
                    }
                    DrawerFilterButton("Tools", contentFilters["tools"] == true, Modifier.weight(1f)) { contentFilters["tools"] = it }
                }
            }
        }
    }

    @Composable
    private fun DrawerFilterButton(
        label: String,
        checked: Boolean,
        modifier: Modifier = Modifier,
        onToggle: (Boolean) -> Unit,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        val bgColor by animateColorAsState(
            targetValue = if (checked) Accent.copy(alpha = 0.2f) else CardDark,
            animationSpec = tween(200),
            label = "filterBg",
        )
        val borderColor by animateColorAsState(
            targetValue = if (checked) Accent else CardBorder,
            animationSpec = tween(200),
            label = "filterBorder",
        )
        val textColor by animateColorAsState(
            targetValue = if (checked) Accent else TextSecondary,
            animationSpec = tween(200),
            label = "filterText",
        )
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
            label = "filterScale",
        )

        Box(
            modifier =
                modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onToggle(!checked) }
                    .padding(vertical = 10.dp, horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }

    // Smart game folder detection
    private fun detectGameFolder(exePath: String): String {
        val exeFile = java.io.File(exePath)
        // Directories that are typically sub-folders inside a game, not the root
        val subDirNames =
            setOf(
                "bin",
                "binaries",
                "x64",
                "x86",
                "win64",
                "win32",
                "bin64",
                "bin32",
                "game",
                "build",
                "release",
                "shipping",
                "debug",
                "retail",
                "dist",
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

        val exePickerLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    // Resolve to a real file path
                    var path = getPathFromContentUri(context, uri)
                    // If path resolution didn't yield an .exe, check the URI display name
                    val isExe = path != null && path.lowercase().endsWith(".exe")
                    if (!isExe && path != null) {
                        // Try getting the display name from ContentResolver as fallback
                        val displayName =
                            try {
                                context.contentResolver
                                    .query(
                                        uri,
                                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                                        null,
                                        null,
                                        null,
                                    )?.use { cursor ->
                                        if (cursor.moveToFirst()) cursor.getString(0) else null
                                    }
                            } catch (_: Exception) {
                                null
                            }
                        if (displayName != null && displayName.lowercase().endsWith(".exe")) {
                            // Path is valid but extension was lost in resolution; append or recombine
                            if (!path.lowercase().endsWith(".exe")) {
                                val parent = java.io.File(path).let { if (it.isDirectory) it else it.parentFile }
                                if (parent != null) {
                                    val reconstructed = java.io.File(parent, displayName)
                                    if (reconstructed.exists()) path = reconstructed.absolutePath
                                }
                            }
                        }
                    }
                    if (path != null && (path.lowercase().endsWith(".exe") || java.io.File(path).exists())) {
                        selectedExePath = path
                        gameFolder = detectGameFolder(path)
                        // Auto-generate a game name from the folder name
                        if (gameName.isBlank()) {
                            gameName =
                                java.io
                                    .File(gameFolder!!)
                                    .name
                                    .replace("_", " ")
                                    .replace("-", " ")
                        }
                    } else {
                        com.winlator.cmod.shared.android.AppUtils.showToast(
                            context,
                            "Please select a .exe file",
                            android.widget.Toast.LENGTH_SHORT,
                        )
                    }
                }
            }

        val folderPickerLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri -> uri?.let { gameFolder = getPathFromTreeUri(it) } }

        val defaultDensity = LocalDensity.current
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(defaultDensity.density, fontScale = 1f),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .widthIn(max = 360.dp)
                            .fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF141B24),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        // Title
                        Text(
                            stringResource(R.string.library_games_add_custom_game),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )

                        Spacer(Modifier.height(10.dp))

                        // Scrollable content area
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            // Pick EXE button
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            if (!ensureAllFilesAccessForImports(context)) return@clickable
                                            exePickerLauncher.launch(
                                                arrayOf(
                                                    "application/octet-stream",
                                                    "application/x-msdos-program",
                                                    "application/x-msdownload",
                                                    "*/*",
                                                ),
                                            )
                                        }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (selectedExePath == null) "Select Executable (.exe)" else java.io.File(selectedExePath!!).name,
                                    color = if (selectedExePath == null) TextSecondary else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                )
                            }

                            if (selectedExePath != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    selectedExePath!!,
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Spacer(Modifier.height(8.dp))

                                // Game name text field — compact
                                OutlinedTextField(
                                    value = gameName,
                                    onValueChange = { gameName = it },
                                    label = { Text(stringResource(R.string.library_games_game_name), fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Accent,
                                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            cursorColor = Accent,
                                            focusedLabelColor = Accent,
                                            unfocusedLabelColor = TextSecondary,
                                        ),
                                    shape = RoundedCornerShape(10.dp),
                                )

                                Spacer(Modifier.height(8.dp))

                                // Game folder — single compact row
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = StatusOnline.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Game Folder (Mapped Game Drive)", color = TextSecondary, fontSize = 9.sp)
                                        Text(
                                            gameFolder ?: "Auto-detected",
                                            color = if (gameFolder != null) TextPrimary else TextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(onClick = {
                                        if (!ensureAllFilesAccessForImports(context)) return@IconButton
                                        folderPickerLauncher.launch(null)
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "Change",
                                            tint = Accent,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.3f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp).widthIn(min = 72.dp),
                            ) {
                                Text(stringResource(R.string.common_ui_cancel), fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            val addEnabled = selectedExePath != null && gameName.isNotBlank() && gameFolder != null && !isAdding
                            OutlinedButton(
                                onClick = {
                                    if (selectedExePath == null || gameName.isBlank() || gameFolder == null) {
                                        com.winlator.cmod.shared.android.AppUtils.showToast(
                                            context,
                                            context.getString(R.string.library_games_select_exe_provide_name),
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                        return@OutlinedButton
                                    }
                                    isAdding = true
                                    scope.launch(Dispatchers.IO) {
                                        addCustomGame(context, gameName.trim(), selectedExePath!!, gameFolder!!)
                                        withContext(Dispatchers.Main) {
                                            isAdding = false
                                            com.winlator.cmod.shared.android.AppUtils.showToast(
                                                context,
                                                "$gameName added!",
                                                android.widget.Toast.LENGTH_SHORT,
                                            )
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = addEnabled,
                                shape = RoundedCornerShape(10.dp),
                                border =
                                    androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (addEnabled) Accent.copy(alpha = 0.5f) else TextSecondary.copy(alpha = 0.2f),
                                    ),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp).widthIn(min = 72.dp),
                            ) {
                                if (isAdding) {
                                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.common_ui_add), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Resolve content URI to real file path
    private fun getPathFromContentUri(
        context: android.content.Context,
        uri: Uri,
    ): String? {
        val displayName =
            try {
                context.contentResolver
                    .query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            } catch (_: Exception) {
                null
            }

        // Try DocumentsContract first
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                // raw: prefix contains the actual filesystem path directly
                if (docId.startsWith("raw:")) {
                    return docId.substringAfter("raw:")
                }
                if (docId.startsWith("primary:")) {
                    return "${android.os.Environment.getExternalStorageDirectory().path}/${docId.substringAfter(":")}"
                }
                // Downloads provider on some Android versions uses "msf:NNN"
                if (docId.startsWith("msf:") || docId.all { it.isDigit() }) {
                    val resolved = queryContentResolverForPath(context, uri)
                    if (resolved != null) return resolved
                    if (displayName != null) {
                        val downloadsFile =
                            java.io.File(
                                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                                displayName,
                            )
                        if (downloadsFile.exists()) return downloadsFile.absolutePath
                    }
                }
                if (docId.contains(":")) {
                    val parts = docId.split(":", limit = 2)
                    if (parts.size == 2 && parts[1].isNotEmpty()) {
                        return "/storage/${parts[0]}/${parts[1]}"
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Try querying ContentResolver for _data column (works for many providers)
        try {
            val resolved = queryContentResolverForPath(context, uri)
            if (resolved != null) return resolved
        } catch (_: Exception) {
        }

        // Fallback: uri.path — strip common prefixes
        val rawPath = uri.path
        if (rawPath != null) {
            // Handle /document/raw:/actual/path format
            val rawPrefix = "/document/raw:"
            if (rawPath.startsWith(rawPrefix)) {
                return rawPath.substringAfter(rawPrefix)
            }
            // Handle /document/primary:path format
            val primaryPrefix = "/document/primary:"
            if (rawPath.startsWith(primaryPrefix)) {
                return "${android.os.Environment.getExternalStorageDirectory().path}/${rawPath.substringAfter(primaryPrefix)}"
            }
            // If the path looks like a real file path, return it
            if (rawPath.startsWith("/storage/") || rawPath.startsWith("/data/")) {
                return rawPath
            }
        }
        if (displayName != null) {
            val downloadsFile =
                java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    displayName,
                )
            if (downloadsFile.exists()) return downloadsFile.absolutePath
        }
        return rawPath
    }

    private fun ensureAllFilesAccessForImports(context: android.content.Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()) {
            return true
        }

        com.winlator.cmod.shared.android.AppUtils.showToast(
            context,
            "Grant All files access to browse Downloads directly.",
            android.widget.Toast.LENGTH_LONG,
        )

        val intent =
            android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
        startActivity(intent)
        return false
    }

    // Query ContentResolver for the actual file path via _data column
    private fun queryContentResolverForPath(
        context: android.content.Context,
        uri: Uri,
    ): String? =
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }

    // Create custom game shortcut + container
    private fun addCustomGame(
        context: android.content.Context,
        name: String,
        exePath: String,
        gameFolderPath: String,
    ) {
        val containerManager = ContainerManager(context)
        var container = SetupWizardActivity.getPreferredGameContainer(context, containerManager)
        if (container == null) {
            SetupWizardActivity.promptToInstallWineOrCreateContainer(context)
            return
        }

        val exeFile = java.io.File(exePath)
        ensureGameDrive(
            container,
            gameFolderPath,
            com.winlator.cmod.runtime.wine.WineUtils
                .getPreferredGameDriveLetter(gameFolderPath),
        )
        val execCmd = buildWineExecCommand(container, gameFolderPath, exeFile)

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
        content.append("use_container_defaults=1\n")
        com.winlator.cmod.shared.io.FileUtils
            .writeString(shortcutFile, content.toString())
        container.saveData()

        // Extract exe icon and save as PNG for carousel artwork
        try {
            val iconOutFile = java.io.File(context.filesDir, "custom_icons/$safeName.png")
            PeIconExtractor.extractAndSave(java.io.File(exePath), iconOutFile)
        } catch (_: Exception) {
        }
    }

    @Composable
    fun CustomPathWarningDialog(
        onDismiss: () -> Unit,
        onProceed: () -> Unit,
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardDark,
                modifier = Modifier.padding(16.dp),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.stores_accounts_custom_download_path),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.stores_accounts_custom_download_path_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.common_ui_close), color = TextSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onProceed,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringResource(R.string.common_ui_proceed))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControllerBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = 22.dp)
                .background(Color(0xFF394048), RoundedCornerShape(15.dp))
                .border(1.dp, Color(0xFF8B949E).copy(alpha = 0.5f), RoundedCornerShape(15.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(0xFFE6EDF3),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 15.sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
