package com.winlator.cmod.app.shell

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
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
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.graphics.TileMode
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
import com.winlator.cmod.app.service.download.DownloadCoordinator
import com.winlator.cmod.app.update.UpdateChecker
import com.winlator.cmod.feature.settings.InputControlsFragment
import com.winlator.cmod.feature.settings.SettingsHost
import com.winlator.cmod.feature.settings.SettingsNavItem
import com.winlator.cmod.feature.setup.SetupWizardActivity
import com.winlator.cmod.feature.shortcuts.LibraryShortcutUtils
import com.winlator.cmod.feature.shortcuts.LibraryShortcutArtwork
import com.winlator.cmod.feature.shortcuts.ShortcutBroadcastReceiver
import com.winlator.cmod.feature.shortcuts.ShortcutSettingsComposeDialog
import com.winlator.cmod.feature.shortcuts.ShortcutsFragment
import com.winlator.cmod.feature.stores.common.StoreArtworkCache
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
import com.winlator.cmod.feature.stores.epic.service.EpicUpdateInfo
import com.winlator.cmod.feature.stores.epic.ui.auth.EpicOAuthActivity
import com.winlator.cmod.feature.stores.gog.data.GOGDlcInfo
import com.winlator.cmod.feature.stores.gog.data.GOGGame
import com.winlator.cmod.feature.stores.gog.data.LibraryItem
import com.winlator.cmod.feature.stores.gog.service.GOGAuthManager
import com.winlator.cmod.feature.stores.gog.service.GOGConstants
import com.winlator.cmod.feature.stores.gog.service.GOGManifestSizes
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.feature.stores.gog.service.GOGUpdateInfo
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
import com.winlator.cmod.feature.sync.CloudSyncHelper
import com.winlator.cmod.feature.sync.google.CloudSyncManager
import com.winlator.cmod.feature.sync.google.GameSaveBackupManager
import com.winlator.cmod.feature.sync.ui.CloudSavesContent
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.runtime.display.XServerDisplayActivity
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.runtime.input.ControllerHelper
import com.winlator.cmod.runtime.wine.PeIconExtractor
import com.winlator.cmod.shared.android.ActivityResultHost
import com.winlator.cmod.shared.android.AppTerminationHelper
import com.winlator.cmod.shared.android.DirectoryPickerDialog
import com.winlator.cmod.shared.android.FixedFontScaleAppCompatActivity
import com.winlator.cmod.shared.android.RefreshRateUtils
import com.winlator.cmod.shared.io.StorageUtils
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.ui.CarouselView
import com.winlator.cmod.shared.ui.dialog.PopupDialog
import com.winlator.cmod.shared.ui.dialog.PopupTextAction
import com.winlator.cmod.shared.ui.FourByTwoGridView
import com.winlator.cmod.shared.ui.JoystickGridScroll
import com.winlator.cmod.shared.ui.JoystickListScroll
import com.winlator.cmod.shared.ui.ListView
import com.winlator.cmod.shared.ui.widget.chasingBorder
import com.winlator.cmod.shared.theme.WinNativeTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.Lazy
import com.winlator.cmod.feature.stores.steam.enums.EPersonaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

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
private val DownloadCardBlack = Color.Black.copy(alpha = 0.46f)
private val DownloadCardSelectedBlack = Color.Black.copy(alpha = 0.58f)
private val DownloadButtonBlack = Color.Black.copy(alpha = 0.38f)
private val DownloadChaseBlue = Color(0xFF2196F3)
private val DownloadChaseSky = Color(0xFF29B6F6)
private val DownloadChaseCyan = Color(0xFF00E5FF)
private val DownloadChaseGradientStops =
    arrayOf(
        0.00f to DownloadChaseBlue,
        0.125f to DownloadChaseSky,
        0.25f to DownloadChaseCyan,
        0.375f to DownloadChaseSky,
        0.50f to DownloadChaseBlue,
        0.625f to DownloadChaseSky,
        0.75f to DownloadChaseCyan,
        0.875f to DownloadChaseSky,
        1.00f to DownloadChaseBlue,
    )
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
    FixedFontScaleAppCompatActivity(),
    ActivityResultHost {
    @Inject lateinit var dbProvider: Lazy<PluviaDatabase>

    private val db: PluviaDatabase
        get() = dbProvider.get()

    private data class PendingNavigation(
        val item: SettingsNavItem = SettingsNavItem.CONTAINERS,
        val profileId: Int = 0,
        val editContainerId: Int = 0,
        val returnToGameOnBack: Boolean = false,
    )

    private data class ControllerConnectionState(
        val isConnected: Boolean = ControllerHelper.isControllerConnected(),
        val isPlayStation: Boolean = ControllerHelper.isPlayStationController(),
    )

    private var rootNavController: NavHostController? = null

    private var pendingNavigation: PendingNavigation? = null

    // Absorb rapid Back presses during the settings exit animation.
    private var isPoppingSettings: Boolean = false

    private var selectedSteamAppId: Int = 0
    private var selectedSteamAppName: String = ""
    private var selectedLibrarySource: String = ""
    private var selectedGogGameId: String = ""

    var libraryRefreshSignal by mutableIntStateOf(0)

    var libraryPlaytimeRefreshSignal by mutableIntStateOf(0)
    private var hasCompletedInitialResume = false

    // Activity-level so task progress survives game-detail dialog teardown.
    private var taskProgressInfo by mutableStateOf<DownloadInfo?>(null)
    private var taskProgressGameName by mutableStateOf("")
    private var taskProgressCompleteMsg by mutableStateOf("")
    private var taskProgressFailedMsg by mutableStateOf("")
    private var taskProgressShown by mutableStateOf(false)
    private var taskDoneMessage by mutableStateOf<String?>(null)
    private var taskDoneFailed by mutableStateOf(false)
    private var taskCheckingShown by mutableStateOf(false)
    private var taskCheckingGameName by mutableStateOf("")

    private var taskProgressCompleteAsToast by mutableStateOf(false)

    private fun showTaskProgressPopup(
        info: DownloadInfo,
        gameName: String,
        completeMsg: String,
        failedMsg: String,
        completeAsToast: Boolean = false,
    ) {
        taskCheckingShown = false
        taskProgressInfo = info
        taskProgressGameName = gameName
        taskProgressCompleteMsg = completeMsg
        taskProgressFailedMsg = failedMsg
        taskProgressCompleteAsToast = completeAsToast
        taskProgressShown = true
        taskDoneMessage = null
    }

    // Prevent overlapping checks after the checking pop-up is dismissed.
    private var updateCheckInProgress = false

    // Runs a Steam update check behind the checking pop-up.
    private fun startUpdateCheck(appId: Int, gameName: String) {
        if (updateCheckInProgress) return
        if (!com.winlator.cmod.app.service.NetworkMonitor.hasInternet.value) {
            com.winlator.cmod.shared.ui.toast.WinToast.show(
                this,
                getString(R.string.downloads_no_internet),
                android.widget.Toast.LENGTH_SHORT,
            )
            return
        }
        updateCheckInProgress = true
        taskCheckingGameName = gameName
        taskCheckingShown = true
        taskDoneMessage = null
        lifecycleScope.launch {
            val result =
                runCatching {
                    withContext(Dispatchers.IO) { SteamService.checkForAppUpdate(appId) }
                }.getOrNull()
            try {
            when {
                result == null || result.message != null -> {
                    taskCheckingShown = false
                    taskDoneFailed = true
                    taskDoneMessage = getString(R.string.store_game_update_check_failed_notice)
                }
                result.hasUpdate -> {
                    val started =
                        withContext(Dispatchers.IO) {
                            SteamService.downloadAppForUpdate(appId, result.depotIds)
                        }
                    if (started != null) {
                        showTaskProgressPopup(
                            started,
                            gameName,
                            getString(R.string.store_game_update_complete),
                            getString(R.string.store_game_update_failed_notice),
                        )
                    } else {
                        // A download is already running — downloadApp showed its toast.
                        taskCheckingShown = false
                    }
                }
                else -> {
                    taskCheckingShown = false
                    taskDoneFailed = false
                    taskDoneMessage = getString(R.string.store_game_no_updates_notice)
                }
            }
            } finally {
                updateCheckInProgress = false
            }
        }
    }

    private fun startGogUpdateCheck(gameId: String, gameName: String) {
        if (updateCheckInProgress) return
        if (!com.winlator.cmod.app.service.NetworkMonitor.hasInternet.value) {
            com.winlator.cmod.shared.ui.toast.WinToast.show(
                this,
                getString(R.string.downloads_no_internet),
                android.widget.Toast.LENGTH_SHORT,
            )
            return
        }
        updateCheckInProgress = true
        taskCheckingGameName = gameName
        taskCheckingShown = true
        taskDoneMessage = null
        lifecycleScope.launch {
            val result =
                runCatching {
                    withContext(Dispatchers.IO) { GOGService.checkForGameUpdate(this@UnifiedActivity, gameId) }
                }.getOrNull()
            try {
                when {
                    result == null || result.message != null -> {
                        taskCheckingShown = false
                        taskDoneFailed = true
                        taskDoneMessage = getString(R.string.store_game_update_check_failed_notice)
                    }
                    result.hasUpdate -> {
                        val started =
                            withContext(Dispatchers.IO) {
                                GOGService.updateGameFiles(this@UnifiedActivity, gameId)
                            }
                        if (started != null) {
                            showTaskProgressPopup(
                                started,
                                gameName,
                                getString(R.string.store_game_update_complete),
                                getString(R.string.store_game_update_failed_notice),
                            )
                        } else {
                            taskCheckingShown = false
                        }
                    }
                    else -> {
                        taskCheckingShown = false
                        taskDoneFailed = false
                        taskDoneMessage = getString(R.string.store_game_no_updates_notice)
                    }
                }
            } finally {
                updateCheckInProgress = false
            }
        }
    }

    private fun startEpicUpdateCheck(appId: Int, gameName: String) {
        if (updateCheckInProgress) return
        if (!com.winlator.cmod.app.service.NetworkMonitor.hasInternet.value) {
            com.winlator.cmod.shared.ui.toast.WinToast.show(
                this,
                getString(R.string.downloads_no_internet),
                android.widget.Toast.LENGTH_SHORT,
            )
            return
        }
        updateCheckInProgress = true
        taskCheckingGameName = gameName
        taskCheckingShown = true
        taskDoneMessage = null
        lifecycleScope.launch {
            val result =
                runCatching {
                    withContext(Dispatchers.IO) { EpicService.checkForGameUpdate(this@UnifiedActivity, appId) }
                }.getOrNull()
            try {
                when {
                    result == null || result.message != null -> {
                        taskCheckingShown = false
                        taskDoneFailed = true
                        taskDoneMessage = getString(R.string.store_game_update_check_failed_notice)
                    }
                    result.hasUpdate -> {
                        val started =
                            withContext(Dispatchers.IO) {
                                EpicService.updateGameFiles(this@UnifiedActivity, appId)
                            }
                        if (started != null) {
                            showTaskProgressPopup(
                                started,
                                gameName,
                                getString(R.string.store_game_update_complete),
                                getString(R.string.store_game_update_failed_notice),
                            )
                        } else {
                            taskCheckingShown = false
                        }
                    }
                    else -> {
                        taskCheckingShown = false
                        taskDoneFailed = false
                        taskDoneMessage = getString(R.string.store_game_no_updates_notice)
                    }
                }
            } finally {
                updateCheckInProgress = false
            }
        }
    }

    // Avoid paying card border animation cost behind full-screen dialogs.
    private val chasingBordersPaused = mutableStateOf(false)

    // Keep first composition light while prefs/auth state and Room warm up.
    private var startupBootstrapReady by mutableStateOf(false)
    private var startupLibraryLayoutMode by mutableStateOf<LibraryLayoutMode?>(null)
    private var startupStoreVisible: Map<String, Boolean>? = null
    private var startupContentFilters: Map<String, Boolean>? = null

    // Lets kept-alive library cards skip animation while their tab is hidden.
    private val libraryTabActive = mutableStateOf(true)

    val rightStickScrollState = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val leftStickScrollState = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val keyEventFlow = kotlinx.coroutines.flow.MutableSharedFlow<android.view.KeyEvent>(extraBufferCapacity = 10)

    val libraryFocusIndex = kotlinx.coroutines.flow.MutableStateFlow(0)
    var libraryItemCount: Int = 0
    private var currentLibraryLayoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID_4

    // Coil model for the focused game's immersive background.
    val immersiveBackgroundRef = kotlinx.coroutines.flow.MutableStateFlow<Any?>(null)

    private val defaultNavigationBarColor: Int = android.graphics.Color.TRANSPARENT

    fun applyImmersiveSystemBars(enabled: Boolean) {
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    val storeFocusIndex = kotlinx.coroutines.flow.MutableStateFlow(0)
    var storeItemCount: Int = 0
    private var storeColumns: Int = 4

    var storeGridState: androidx.compose.foundation.lazy.grid.LazyGridState? = null

    // Shared gate for d-pad, hat, and joystick navigation events.
    private var lastMoveTime = 0L

    private var dpadHeld = false
    private var joystickActive = false

    companion object {
        private const val MOVE_INTERVAL_MS = 250L
        private var instance: UnifiedActivity? = null

        fun refreshLibrary() {
            instance?.let { it.libraryRefreshSignal++ }
        }

        /** Currently attached Activity (or null if the app is fully backgrounded/killed). */
        @JvmStatic
        fun currentActivity(): UnifiedActivity? =
            instance?.takeUnless { it.isFinishing || it.isDestroyed }
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

    override fun launchWallpaperImagePicker() {
        wallpaperImagePickerLauncher.launch("image/*")
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

        // Snap to visible content before applying another store-grid move.
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
                    return
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

    // Avoid fragment tree traversal on every input event.
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
        cachedInputControlsFragment?.let { fragment ->
            if (fragment.dispatchKeyEvent(event)) return true
        }

        val keyCode = event.keyCode
        val action = event.action

        // Prevent global controller buttons from falling through to launch actions.
        val isHandledGlobally =
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_BUTTON_START,
                android.view.KeyEvent.KEYCODE_BUTTON_A,
                android.view.KeyEvent.KEYCODE_BUTTON_B,
                android.view.KeyEvent.KEYCODE_BUTTON_X,
                android.view.KeyEvent.KEYCODE_BUTTON_Y,
                android.view.KeyEvent.KEYCODE_BUTTON_L1,
                android.view.KeyEvent.KEYCODE_BUTTON_R1,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                -> true

                else -> false
            }

        val isDpad =
            keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
        if (isDpad) {
            if (action == android.view.KeyEvent.ACTION_UP) {
                dpadHeld = false
                return true
            }
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                val now = android.os.SystemClock.uptimeMillis()
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
            return true
        }

        if (action == android.view.KeyEvent.ACTION_DOWN) {
            if (isHandledGlobally) {
                keyEventFlow.tryEmit(event)
                return true
            }
        } else if (action == android.view.KeyEvent.ACTION_UP && isHandledGlobally) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        chasingBordersPaused.value = true
        UpdateChecker.stopBackgroundLoop()
        UpdateChecker.cancelPostGameCheck()
    }

    override fun onResume() {
        super.onResume()
        chasingBordersPaused.value = false
        if (hasCompletedInitialResume) {
            libraryPlaytimeRefreshSignal++
        } else {
            hasCompletedInitialResume = true
        }

        UpdateChecker.startBackgroundLoop(this)
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            DownloadService.clearCompletedDownloads()
        }
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(inputControlsFragmentTracker)
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        cachedInputControlsFragment?.let { fragment ->
            if (fragment.dispatchGenericMotionEvent(event)) return true
        }

        if ((event.source and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK &&
            event.action == android.view.MotionEvent.ACTION_MOVE
        ) {
            val rz = event.getAxisValue(android.view.MotionEvent.AXIS_RZ)
            rightStickScrollState.value = rz

            val leftY = event.getAxisValue(android.view.MotionEvent.AXIS_Y)
            leftStickScrollState.value = leftY

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
                joystickActive = false
                lastMoveTime = 0L
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private var currentTabKey: String = "library"

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
        returnToGameOnBack: Boolean = false,
    ) {
        // In-activity settings navigation does not trigger Activity resume.
        reapplyPreferredRefreshRate()
        val route = buildSettingsRoute(item, profileId, editContainerId, returnToGameOnBack)
        val nav = rootNavController
        if (nav == null) {
            pendingNavigation = PendingNavigation(item, profileId, editContainerId, returnToGameOnBack)
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
        returnToGameOnBack: Boolean = false,
    ): String =
        "settings?item=${item.name}&profileId=$profileId&editContainerId=$editContainerId&returnToGameOnBack=$returnToGameOnBack"

    private fun extractSettingsNavigation(intent: Intent?): PendingNavigation? {
        if (intent == null) return null

        val editContainerId = intent.getIntExtra("edit_container_id", 0)
        if (editContainerId > 0) {
            return PendingNavigation(SettingsNavItem.CONTAINERS, 0, editContainerId)
        }

        if (intent.getBooleanExtra("edit_input_controls", false)) {
            val profileId = intent.getIntExtra("selected_profile_id", 0)
            val returnToGameOnBack = intent.getBooleanExtra("return_to_game_on_back", false)
            return PendingNavigation(SettingsNavItem.INPUT_CONTROLS, profileId, 0, returnToGameOnBack)
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
        intent.removeExtra("return_to_game_on_back")
    }

    private fun handleSettingsIntent(intent: Intent?) {
        val request = extractSettingsNavigation(intent) ?: return
        consumeSettingsIntent(intent)
        navigateToSettings(request.item, request.profileId, request.editContainerId, request.returnToGameOnBack)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSettingsIntent(intent)
    }

    private fun bootstrapStartupState() {
        startupBootstrapReady = false
        startupLibraryLayoutMode = null
        startupStoreVisible = null
        startupContentFilters = null

        lifecycleScope.launch(Dispatchers.IO) {
            val appContext = applicationContext
            val resolvedLayoutMode =
                runCatching {
                    PrefManager.init(appContext)
                    LibraryLayoutMode.valueOf(PrefManager.libraryLayoutMode)
                }.getOrElse { error ->
                    Log.w("UnifiedActivity", "Failed to resolve initial library layout", error)
                    LibraryLayoutMode.GRID_4
                }

            val resolvedStoreVisible =
                runCatching {
                    val saved = PrefManager.libraryStoreVisible.split(",").toSet()
                    mapOf("steam" to ("steam" in saved), "epic" to ("epic" in saved), "gog" to ("gog" in saved))
                }.getOrElse { mapOf("steam" to true, "epic" to true, "gog" to true) }

            val resolvedContentFilters =
                runCatching {
                    val saved = PrefManager.libraryContentFilters.split(",").toSet()
                    mapOf(
                        "games" to ("games" in saved),
                        "dlc" to ("dlc" in saved),
                        "applications" to ("applications" in saved),
                        "tools" to ("tools" in saved),
                    )
                }.getOrElse { mapOf("games" to true, "dlc" to false, "applications" to false, "tools" to false) }

            runCatching { dbProvider.get() }
                .onFailure { Log.w("UnifiedActivity", "Database warmup failed", it) }
            runCatching { EpicAuthManager.updateLoginStatus(appContext) }
                .onFailure { Log.w("UnifiedActivity", "Epic auth warmup failed", it) }
            runCatching { GOGAuthManager.updateLoginStatus(appContext) }
                .onFailure { Log.w("UnifiedActivity", "GOG auth warmup failed", it) }
            runCatching { SteamService.initLoginStatus(appContext) }
                .onFailure { Log.w("UnifiedActivity", "Steam auth warmup failed", it) }

            withContext(Dispatchers.Main.immediate) {
                startupLibraryLayoutMode = resolvedLayoutMode
                currentLibraryLayoutMode = resolvedLayoutMode
                startupStoreVisible = resolvedStoreVisible
                startupContentFilters = resolvedContentFilters
                startupBootstrapReady = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        instance = this
        super.onCreate(savedInstanceState)
        if (!SetupWizardActivity.isSetupComplete(this) || !ImageFs.find(this).isUpToDate) {
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
        bootstrapStartupState()

        // Surface store-session events as toasts.
        lifecycleScope.launch {
            com.winlator.cmod.feature.stores.common.StoreSessionBus.events.collect { event ->
                val label =
                    when (event.store) {
                        com.winlator.cmod.feature.stores.common.Store.EPIC -> "Epic"
                        com.winlator.cmod.feature.stores.common.Store.GOG -> "GOG"
                        com.winlator.cmod.feature.stores.common.Store.STEAM -> "Steam"
                    }
                when (event) {
                    is com.winlator.cmod.feature.stores.common.StoreSessionEvent.SessionExpired -> {
                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                            this@UnifiedActivity,
                            "$label session expired — please sign in again",
                            android.widget.Toast.LENGTH_LONG,
                        )
                    }
                    is com.winlator.cmod.feature.stores.common.StoreSessionEvent.SessionRestored -> Unit
                    is com.winlator.cmod.feature.stores.common.StoreSessionEvent.SessionRefreshed -> {
                        Unit
                    }
                }
            }
        }

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val initialSettingsNavigation = extractSettingsNavigation(intent)
        if (initialSettingsNavigation != null) {
            consumeSettingsIntent(intent)
        }

        // Exclude the drawer edge from system back gesture where Android allows it.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val decorView = window.decorView
            val updateDrawerGestureExclusion = {
                val leftEdgeWidth = (32 * resources.displayMetrics.density).toInt()
                val exclusionRect = android.graphics.Rect(0, 0, leftEdgeWidth, decorView.height)
                decorView.systemGestureExclusionRects = listOf(exclusionRect)
            }
            decorView.post(updateDrawerGestureExclusion)
            decorView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateDrawerGestureExclusion()
            }
        }

        setContent {
            val navController = rememberNavController()
            rootNavController = navController

            LaunchedEffect(Unit) {
                val pending = pendingNavigation
                if (pending != null) {
                    navigateToSettings(
                        pending.item,
                        pending.profileId,
                        pending.editContainerId,
                        pending.returnToGameOnBack,
                    )
                    pendingNavigation = null
                } else {
                    handleSettingsIntent(intent)
                }
            }

            WinNativeTheme(
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
                            buildSettingsRoute(it.item, it.profileId, it.editContainerId, it.returnToGameOnBack)
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
                        LaunchedEffect(Unit) { isPoppingSettings = false }
                        UnifiedHub()
                    }
                    composable(
                        "settings?item={item}&profileId={profileId}&editContainerId={editContainerId}&returnToGameOnBack={returnToGameOnBack}",
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
                                navArgument("returnToGameOnBack") {
                                    type = NavType.BoolType
                                    defaultValue = false
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
                        val returnToGameOnBack =
                            backStackEntry.arguments?.getBoolean("returnToGameOnBack") ?: false

                        val exitSettingsToHubOnce: () -> Unit = {
                            if (!isPoppingSettings) {
                                isPoppingSettings = true
                                if (returnToGameOnBack) {
                                    setResult(android.app.Activity.RESULT_OK)
                                    finish()
                                } else {
                                    val poppedToHub = navController.popBackStack("hub", inclusive = false)
                                    if (!poppedToHub) {
                                        navController.navigate("hub") {
                                            popUpTo(navController.graph.startDestinationId) {
                                                inclusive = true
                                            }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        }

                        SettingsHost(
                            startItem = startItem,
                            selectedProfileId = profileId,
                            bordersPaused = chasingBordersPaused.value,
                            onBack = exitSettingsToHubOnce,
                        )
                        BackHandler(enabled = true) { exitSettingsToHubOnce() }

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
                                        ) { exitSettingsToHubOnce() }
                                        .show()
                                } else {
                                    exitSettingsToHubOnce()
                                }
                            }
                        }
                    }
                }

                TaskProgressHost()
            }
        }
        scheduleDeferredStoreBootstrap()
    }

    private fun scheduleDeferredStoreBootstrap() {
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            lifecycleScope.launch(Dispatchers.IO) {
                if (EpicService.hasStoredCredentials(this@UnifiedActivity)) {
                    EpicService.start(this@UnifiedActivity)
                    // Keep token validation off the first-frame path.
                    EpicAuthManager.getStoredCredentials(this@UnifiedActivity)
                    com.winlator.cmod.feature.stores.epic.service.EpicTokenRefreshWorker
                        .schedule(this@UnifiedActivity)
                }

                if (SteamService.hasStoredCredentials(this@UnifiedActivity)) {
                    SteamService.start(this@UnifiedActivity)
                }

                if (GOGAuthManager.isLoggedIn(this@UnifiedActivity)) {
                    GOGService.start(this@UnifiedActivity)
                }

                SteamService.maybeRepairInstalledMetadataOnStartup(this@UnifiedActivity)
            }
        }
    }

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

    @Composable
    private fun rememberSteamInstallStateMap(apps: List<SteamApp>): Map<Int, Boolean> {
        var installStateMap by remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }

        LaunchedEffect(apps) {
            installStateMap =
                withContext(Dispatchers.IO) {
                    apps.associate { it.id to SteamService.isAppInstalled(it.id) }
                }
        }

        return installStateMap
    }

    @Composable
    private fun <K> rememberInstallPathStateMap(entries: List<Pair<K, String?>>): Map<K, Boolean>
        where K : Any {
        var installStateMap by remember { mutableStateOf<Map<K, Boolean>>(emptyMap()) }

        LaunchedEffect(entries) {
            installStateMap =
                withContext(Dispatchers.IO) {
                    entries.associate { (key, path) ->
                        key to (path?.isNotBlank() == true && java.io.File(path).exists())
                    }
                }
        }

        return installStateMap
    }

    @Composable
    private fun rememberEpicInstallStateMap(
        context: android.content.Context,
        apps: List<EpicGame>,
    ): Map<Int, Boolean> {
        var installStateMap by remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }

        LaunchedEffect(apps) {
            installStateMap =
                withContext(Dispatchers.IO) {
                    apps.associate { it.id to EpicService.isGameInstalled(context, it.id) }
                }
        }

        return installStateMap
    }

    @Composable
    private fun rememberGogInstallStateMap(apps: List<GOGGame>): Map<String, Boolean> {
        var installStateMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

        LaunchedEffect(apps) {
            installStateMap =
                withContext(Dispatchers.IO) {
                    apps.associate { it.id to GOGService.isGameInstalled(it.id) }
                }
        }

        return installStateMap
    }

    @Composable
    fun UnifiedHub() {
        val horizontalNavigationInsets =
            WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
        val initialLibraryLayoutMode = startupLibraryLayoutMode
        val initialStoreVisible = startupStoreVisible ?: mapOf("steam" to true, "epic" to true, "gog" to true)
        val initialContentFilters = startupContentFilters ?: mapOf("games" to true, "dlc" to false, "applications" to false, "tools" to false)
        if (!startupBootstrapReady || initialLibraryLayoutMode == null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(BgDark)
                        .windowInsetsPadding(horizontalNavigationInsets),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = Accent)
                    Text(
                        text = stringResource(R.string.common_ui_app_name),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            return
        }

        val storeVisible = remember { mutableStateMapOf(*initialStoreVisible.entries.map { it.key to it.value }.toTypedArray()) }
        var showAddCustomGame by remember { mutableStateOf(false) }
        var showExitDialog by remember { mutableStateOf(false) }
        var searchQueryTfv by remember { mutableStateOf(TextFieldValue("")) }
        val searchQuery = searchQueryTfv.text
        var localLibraryRefreshKey by remember { mutableIntStateOf(0) }
        var shortcutDataRefreshKey by remember { mutableIntStateOf(0) }
        var iconRefreshKey by remember { mutableIntStateOf(0) }

        val currentRefreshSignal = this@UnifiedActivity.libraryRefreshSignal
        val libraryRefreshKey = currentRefreshSignal + localLibraryRefreshKey
        val shortcutRefreshKey = libraryRefreshKey + shortcutDataRefreshKey
        val playtimeRefreshKey = this@UnifiedActivity.libraryPlaytimeRefreshSignal

        val contentFilters = remember { mutableStateMapOf(*initialContentFilters.entries.map { it.key to it.value }.toTypedArray()) }
        var libraryLayoutMode by remember {
            mutableStateOf(
                initialLibraryLayoutMode,
            )
        }
        var immersiveMode by remember { mutableStateOf(PrefManager.libraryImmersiveMode) }
        var immersiveBlur by remember { mutableStateOf(PrefManager.libraryImmersiveBlur) }
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

        val epicApps by db.epicGameDao().getAll().collectAsState(initial = emptyList())
        val gogApps by db.gogGameDao().getAll().collectAsState(initial = emptyList())

        val controllerState = rememberControllerConnectionState()
        val isControllerConnected = controllerState.isConnected
        val isPS = controllerState.isPlayStation
        val isLibraryTab = tabs.getOrNull(selectedIdx)?.key == "library"

        val libraryRefreshListener =
            remember {
                object : EventDispatcher.JavaEventListener {
                    override fun onEvent(event: Any) {
                        when (event) {
                            is AndroidEvent.LibraryInstallStatusChanged -> {
                                localLibraryRefreshKey++
                                shortcutDataRefreshKey++
                                iconRefreshKey++
                            }
                            is AndroidEvent.LibraryArtworkChanged -> {
                                shortcutDataRefreshKey++
                                iconRefreshKey++
                            }
                        }
                    }
                }
            }
        DisposableEffect(libraryRefreshListener) {
            PluviaApp.events.onJava(AndroidEvent.LibraryInstallStatusChanged::class, libraryRefreshListener)
            PluviaApp.events.onJava(AndroidEvent.LibraryArtworkChanged::class, libraryRefreshListener)
            onDispose {
                PluviaApp.events.offJava(AndroidEvent.LibraryInstallStatusChanged::class, libraryRefreshListener)
                PluviaApp.events.offJava(AndroidEvent.LibraryArtworkChanged::class, libraryRefreshListener)
            }
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
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
                                    context,
                                    R.string.stores_accounts_logged_in_epic,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            } else {
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
                                    context,
                                    R.string.stores_accounts_logged_in_gog,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            } else {
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
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

        var globalSettingsApp by remember { mutableStateOf<SteamApp?>(null) }
        var globalSettingsGogGame by remember { mutableStateOf<GOGGame?>(null) }

        LaunchedEffect(tabs.size) { if (selectedIdx >= tabs.size) selectedIdx = 0 }
        LaunchedEffect(isLoggedIn, persona) {
            if (isLoggedIn && persona == null) {
                SteamService.requestUserPersona()
            }
        }

        val activity = LocalContext.current as? UnifiedActivity

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
                                if (steam != null) {
                                    launchSteamGame(context, containerManager, steam)
                                }
                            }
                        } else if (key != "library" && key != "downloads") {
                            storeItemClickCallback?.invoke(storeFocusIndex.value)
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
                    immersiveMode = immersiveMode,
                    immersiveBlur = immersiveBlur,
                    onLibraryLayoutSelected = {
                        libraryLayoutMode = it
                        PrefManager.libraryLayoutMode = it.name
                    },
                    onStoreVisibleChanged = { key, value ->
                        storeVisible[key] = value
                        PrefManager.libraryStoreVisible = storeVisible.entries.filter { it.value }.joinToString(",") { it.key }
                    },
                    onContentFiltersChanged = { key, value ->
                        contentFilters[key] = value
                        PrefManager.libraryContentFilters = contentFilters.entries.filter { it.value }.joinToString(",") { it.key }
                    },
                    onImmersiveModeChanged = {
                        immersiveMode = it
                        PrefManager.libraryImmersiveMode = it
                    },
                    onImmersiveBlurChanged = {
                        immersiveBlur = it
                        PrefManager.libraryImmersiveBlur = it
                    },
                    onExitApp = {
                        AppTerminationHelper.exitApplication(this@UnifiedActivity, "hub_drawer_exit")
                    },
                )
            },
            scrimColor = Color.Black.copy(alpha = 0.5f),
            gesturesEnabled = drawerState.isOpen,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BgDark)
                    .windowInsetsPadding(horizontalNavigationInsets),
            ) {
                val currentTabKeyForImmersive = tabs.getOrNull(selectedIdx)?.key ?: "library"
                val immersiveActive = immersiveMode && currentTabKeyForImmersive == "library"
                DisposableEffect(immersiveActive) {
                    applyImmersiveSystemBars(immersiveActive)
                    onDispose { applyImmersiveSystemBars(false) }
                }
                if (immersiveMode && currentTabKeyForImmersive == "library") {
                    val immersiveModel by immersiveBackgroundRef.collectAsState()
                    val immersiveRequest =
                        remember(immersiveModel, context) {
                            val builder = ImageRequest.Builder(context).data(immersiveModel)
                            (immersiveModel as? java.io.File)?.takeIf { it.isFile }?.let { file ->
                                // Custom uploads can be overwritten in place.
                                val key = "library_immersive_bg:${file.absolutePath}:${file.lastModified()}"
                                builder.memoryCacheKey(key).diskCacheKey(key)
                            }
                            builder.crossfade(400).build()
                        }
                    AnimatedVisibility(
                        visible = immersiveModel != null,
                        enter = fadeIn(tween(400)),
                        exit = fadeOut(tween(400)),
                        modifier = Modifier.matchParentSize(),
                    ) {
                        Box(Modifier.matchParentSize()) {
                            val immersiveBlurRadius = with(LocalDensity.current) { 8f.toDp() }
                            val blurModifier =
                                if (immersiveBlur) Modifier.blur(immersiveBlurRadius) else Modifier
                            AsyncImage(
                                model = immersiveRequest,
                                contentDescription = null,
                                modifier = Modifier.matchParentSize().then(blurModifier),
                                contentScale = ContentScale.Crop,
                            )
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(BgDark.copy(alpha = 0.5f)),
                            )
                        }
                    }
                }
                val scaffoldContainer = if (immersiveMode && currentTabKeyForImmersive == "library") Color.Transparent else BgDark
                Scaffold(
                    containerColor = scaffoldContainer,
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
                                globalSettingsApp = (
                                    steamApps.find { it.id == selectedSteamAppId }
                                        ?: if (selectedSteamAppId < 0) {
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
                        storeFocusIndex.value = 0
                        storeItemClickCallback = null
                    }

                    val key = tabs.getOrNull(selectedIdx)?.key ?: "library"
                    val innerBoxBg = if (immersiveMode && key == "library") Color.Transparent else BgDark

                    Box(Modifier.padding(padding).fillMaxSize().background(innerBoxBg)) {

                        LaunchedEffect(key) { libraryTabActive.value = (key == "library") }

                        // Keep Library composed so its state survives tab switches.
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
                                shortcutRefreshKey = shortcutRefreshKey,
                                playtimeRefreshKey = playtimeRefreshKey,
                                iconRefreshKey = iconRefreshKey,
                                searchQuery = searchQuery,
                                isControllerConnected = isControllerConnected,
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
                                        DownloadsTab(
                                            selectedDownloadId,
                                            animationsActive = key == "downloads",
                                            onSelectDownload = { selectedDownloadId = it },
                                        )
                                    }

                                    "steam" -> {
                                        SteamStoreTab(isLoggedIn, filteredSteamApps, searchQuery, LibraryLayoutMode.GRID_4)
                                    }

                                    "epic" -> {
                                        EpicStoreTab(isEpicLoggedIn, epicApps, searchQuery, LibraryLayoutMode.GRID_4) {
                                            epicLoginLauncher.launch(Intent(this@UnifiedActivity, EpicOAuthActivity::class.java))
                                        }
                                    }

                                    "gog" -> {
                                        GOGStoreTab(isGogLoggedIn, gogApps, searchQuery, LibraryLayoutMode.GRID_4) {
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

                        if (key == "library") {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .windowInsetsPadding(
                                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
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

                        if (drawerState.isClosed) {
                            DrawerSwipeHotZone(
                                modifier = Modifier.align(Alignment.CenterStart),
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                            )
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
                localLibraryRefreshKey++
            })
        }

        BackHandler(enabled = true) {
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
                            OutlinedButton(
                                onClick = { showExitDialog = false },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.common_ui_cancel), fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = {
                                    AppTerminationHelper.exitApplication(this@UnifiedActivity, "hub_exit_menu")
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

    @Composable
    private fun DrawerSwipeHotZone(
        modifier: Modifier = Modifier,
        onOpenDrawer: () -> Unit,
    ) {
        val density = LocalDensity.current
        val openThresholdPx = with(density) { 36.dp.toPx() }

        Box(
            modifier =
                modifier
                    .fillMaxHeight()
                    .width(40.dp)
                    .pointerInput(openThresholdPx) {
                        var accumulatedDrag = 0f
                        var opened = false

                        detectHorizontalDragGestures(
                            onDragStart = {
                                accumulatedDrag = 0f
                                opened = false
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (dragAmount <= 0f || opened) return@detectHorizontalDragGestures

                                accumulatedDrag += dragAmount
                                change.consume()

                                if (accumulatedDrag >= openThresholdPx) {
                                    opened = true
                                    onOpenDrawer()
                                }
                            },
                        )
                    },
        )
    }

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
                        val tabSideGutter = 12.dp
                        val tabBarShape = RoundedCornerShape(18.dp)
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
                                    .width(tabWidth * visibleCount + tabSideGutter * 2)
                                    .height(44.dp)
                                    .shadow(8.dp, tabBarShape, spotColor = Color.Black.copy(alpha = 0.5f))
                                    .clip(tabBarShape)
                                    .background(CardDark)
                                    .border(1.dp, CardBorder, tabBarShape),
                        ) {
                            LazyRow(
                                state = tabListState,
                                flingBehavior = snapFlingBehavior,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .width(tabWidth * visibleCount)
                                        .fillMaxHeight()
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

                Row(
                    modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

    @Composable
    fun LibraryCarousel(
        isLoggedIn: Boolean,
        steamApps: List<SteamApp>,
        epicApps: List<EpicGame>,
        gogApps: List<GOGGame>,
        layoutMode: LibraryLayoutMode,
        libraryRefreshKey: Int = 0,
        shortcutRefreshKey: Int = 0,
        playtimeRefreshKey: Int = 0,
        iconRefreshKey: Int = 0,
        searchQuery: String = "",
        isControllerConnected: Boolean = false,
    ) {
        val context = LocalContext.current

        var cachedShortcuts by remember { mutableStateOf<List<Shortcut>>(emptyList()) }
        var customApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        var localLibraryRefreshKey by remember { mutableIntStateOf(0) }
        var shortcutsLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(shortcutRefreshKey, localLibraryRefreshKey) {
            shortcutsLoaded = false

            val shortcutScanResult =
                runCatching {
                    withContext(Dispatchers.IO) {
                        val cm = ContainerManager(context)
                        cm.upgradeShortcuts {
                            localLibraryRefreshKey++
                        }
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
                                    
                                    val uuid = shortcut.getExtra("uuid")
                                    val customId = if (uuid.isNotEmpty()) {
                                        -(uuid.hashCode().and(0x7FFFFFFF) + 1)
                                    } else {
                                        -(displayName.hashCode().and(0x7FFFFFFF) + 1)
                                    }

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

                        allShortcuts to apps
                    }
                }.getOrNull()

            if (shortcutScanResult != null) {
                cachedShortcuts = shortcutScanResult.first
                customApps = shortcutScanResult.second
            }

            shortcutsLoaded = true
        }

        // Move library filtering and file checks off the main thread.
        var mergedInstalledApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        var installedApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        var stableInstalledApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        var gogByPseudoId by remember { mutableStateOf<Map<Int, GOGGame>>(emptyMap()) }
        var epicByPseudoId by remember { mutableStateOf<Map<Int, EpicGame>>(emptyMap()) }
        var stableGogByPseudoId by remember { mutableStateOf<Map<Int, GOGGame>>(emptyMap()) }
        var stableEpicByPseudoId by remember { mutableStateOf<Map<Int, EpicGame>>(emptyMap()) }
        var customArtworkPathByAppId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var customGridArtworkPathByAppId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var customCarouselArtworkPathByAppId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var customListArtworkPathByAppId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var customIconPathByAppId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var stableCustomArtworkPathByAppId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var stableCustomGridArtworkPathByAppId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var stableCustomCarouselArtworkPathByAppId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var stableCustomListArtworkPathByAppId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var stableCustomIconPathByAppId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
        var artworkCacheRefreshKey by remember { mutableIntStateOf(0) }
        var libraryLoaded by remember { mutableStateOf(false) }
        // Suppress transient empty states before background recomputation starts.
        val scanInputToken =
            remember(steamApps, epicApps, gogApps, customApps, libraryRefreshKey) { Any() }
        var processedScanToken by remember { mutableStateOf<Any?>(null) }

        LaunchedEffect(scanInputToken) {
            withContext(Dispatchers.IO) {
                val steamInstalled = steamApps.filter { SteamService.isAppInstalled(it.id) }

                val epicInstalled = epicApps.filter { it.isInstalled }

                // Match Epic's DB-backed install filter during verify/update.
                val gogInstalled = gogApps.filter { it.isInstalled }

                val gogMap = gogInstalled.associateBy { gogPseudoId(it.id) }
                val epicMap = epicInstalled.associateBy { 2000000000 + it.id }

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
                    epicByPseudoId = epicMap
                    mergedInstalledApps = merged
                    installedApps = sorted
                    if (sorted.isNotEmpty()) {
                        stableInstalledApps = sorted
                        stableGogByPseudoId = gogMap
                        stableEpicByPseudoId = epicMap
                    }
                    libraryLoaded = true
                    processedScanToken = scanInputToken
                }
            }
        }

        LaunchedEffect(installedApps, gogByPseudoId, cachedShortcuts, iconRefreshKey) {
            val appsSnapshot = installedApps
            val gogSnapshot = gogByPseudoId
            val shortcutsSnapshot = cachedShortcuts

            val artworkPaths =
                withContext(Dispatchers.IO) {
                    buildMap<Int, String> {
                        appsSnapshot.forEach { app ->
                            val gogGame = gogSnapshot[app.id]
                            val isCustom = app.id < 0
                            val isEpic = app.id >= 2000000000
                            val epicId = if (isEpic) app.id - 2000000000 else 0
                            val shortcut =
                                if (gogGame != null) {
                                    shortcutsSnapshot.find {
                                        it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == gogGame.id
                                    }
                                } else {
                                    findShortcutForGame(shortcutsSnapshot, app, isCustom, isEpic, epicId)
                                }
                            val customPath =
                                shortcut
                                    ?.getExtra("customLibraryIconPath")
                                    ?.ifBlank { shortcut.getExtra("customCoverArtPath") }
                            if (!customPath.isNullOrBlank() && java.io.File(customPath).exists()) {
                                put(app.id, customPath)
                            }
                        }
                    }
                }

            val gridArtworkPaths =
                withContext(Dispatchers.IO) {
                    buildMap<Int, String> {
                        appsSnapshot.forEach { app ->
                            val gogGame = gogSnapshot[app.id]
                            val isCustom = app.id < 0
                            val isEpic = app.id >= 2000000000
                            val epicId = if (isEpic) app.id - 2000000000 else 0
                            val shortcut =
                                if (gogGame != null) {
                                    shortcutsSnapshot.find {
                                        it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == gogGame.id
                                    }
                                } else {
                                    findShortcutForGame(shortcutsSnapshot, app, isCustom, isEpic, epicId)
                                }
                            val customPath = shortcut?.getExtra(LibraryShortcutArtwork.LibraryArtworkSlot.GRID.extraKey)
                            if (!customPath.isNullOrBlank() && java.io.File(customPath).exists()) {
                                put(app.id, customPath)
                            }
                        }
                    }
                }

            val carouselArtworkPaths =
                withContext(Dispatchers.IO) {
                    buildMap<Int, String> {
                        appsSnapshot.forEach { app ->
                            val gogGame = gogSnapshot[app.id]
                            val isCustom = app.id < 0
                            val isEpic = app.id >= 2000000000
                            val epicId = if (isEpic) app.id - 2000000000 else 0
                            val shortcut =
                                if (gogGame != null) {
                                    shortcutsSnapshot.find {
                                        it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == gogGame.id
                                    }
                                } else {
                                    findShortcutForGame(shortcutsSnapshot, app, isCustom, isEpic, epicId)
                                }
                            val customPath = shortcut?.getExtra(LibraryShortcutArtwork.LibraryArtworkSlot.CAROUSEL.extraKey)
                            if (!customPath.isNullOrBlank() && java.io.File(customPath).exists()) {
                                put(app.id, customPath)
                            }
                        }
                    }
                }

            val listArtworkPaths =
                withContext(Dispatchers.IO) {
                    buildMap<Int, String> {
                        appsSnapshot.forEach { app ->
                            val gogGame = gogSnapshot[app.id]
                            val isCustom = app.id < 0
                            val isEpic = app.id >= 2000000000
                            val epicId = if (isEpic) app.id - 2000000000 else 0
                            val shortcut =
                                if (gogGame != null) {
                                    shortcutsSnapshot.find {
                                        it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == gogGame.id
                                    }
                                } else {
                                    findShortcutForGame(shortcutsSnapshot, app, isCustom, isEpic, epicId)
                                }
                            val customPath = shortcut?.getExtra(LibraryShortcutArtwork.LibraryArtworkSlot.LIST.extraKey)
                            if (!customPath.isNullOrBlank() && java.io.File(customPath).exists()) {
                                put(app.id, customPath)
                            }
                        }
                    }
                }

            val customIconPaths =
                withContext(Dispatchers.IO) {
                    buildMap<Int, String> {
                        appsSnapshot.forEach { app ->
                            if (app.id >= 0) return@forEach
                            val safeName = app.name.replace("/", "_").replace("\\", "_")
                            val iconFile = java.io.File(context.filesDir, "custom_icons/$safeName.png")
                            if (iconFile.exists()) {
                                put(app.id, iconFile.absolutePath)
                            }
                        }
                    }
                }

            customArtworkPathByAppId = artworkPaths
            customGridArtworkPathByAppId = gridArtworkPaths
            customCarouselArtworkPathByAppId = carouselArtworkPaths
            customListArtworkPathByAppId = listArtworkPaths
            customIconPathByAppId = customIconPaths
            if (appsSnapshot.isNotEmpty()) {
                stableCustomArtworkPathByAppId = artworkPaths
                stableCustomGridArtworkPathByAppId = gridArtworkPaths
                stableCustomCarouselArtworkPathByAppId = carouselArtworkPaths
                stableCustomListArtworkPathByAppId = listArtworkPaths
                stableCustomIconPathByAppId = customIconPaths
            }
        }

        LaunchedEffect(mergedInstalledApps, playtimeRefreshKey) {
            if (mergedInstalledApps.isEmpty()) {
                installedApps = emptyList()
                return@LaunchedEffect
            }

            val sorted =
                withContext(Dispatchers.IO) {
                    val playtimePrefs = context.getSharedPreferences("playtime_stats", android.content.Context.MODE_PRIVATE)
                    val allPlaytime = playtimePrefs.all
                    mergedInstalledApps.sortedByDescending { app ->
                        val searchKey =
                            if (app.id >= 2000000000 || app.id < 0) {
                                app.name
                            } else {
                                app.name.replace(LIBRARY_NAME_SANITIZE_REGEX, "")
                            }
                        (allPlaytime["${searchKey}_last_played"] as? Long) ?: 0L
                    }
                }

            installedApps = sorted
        }

        val awaitingShortcutScan = installedApps.isEmpty() && !shortcutsLoaded
        val keepPreviousLibraryVisible =
            installedApps.isEmpty() &&
                stableInstalledApps.isNotEmpty() &&
                (processedScanToken !== scanInputToken || awaitingShortcutScan)
        val visibleInstalledApps = if (keepPreviousLibraryVisible) stableInstalledApps else installedApps
        val visibleGogByPseudoId = if (keepPreviousLibraryVisible) stableGogByPseudoId else gogByPseudoId
        val visibleEpicByPseudoId = if (keepPreviousLibraryVisible) stableEpicByPseudoId else epicByPseudoId
        val visibleCustomArtworkPathByAppId =
            if (keepPreviousLibraryVisible) stableCustomArtworkPathByAppId else customArtworkPathByAppId
        val visibleCustomGridArtworkPathByAppId =
            if (keepPreviousLibraryVisible) stableCustomGridArtworkPathByAppId else customGridArtworkPathByAppId
        val visibleCustomCarouselArtworkPathByAppId =
            if (keepPreviousLibraryVisible) stableCustomCarouselArtworkPathByAppId else customCarouselArtworkPathByAppId
        val visibleCustomListArtworkPathByAppId =
            if (keepPreviousLibraryVisible) stableCustomListArtworkPathByAppId else customListArtworkPathByAppId
        val visibleCustomIconPathByAppId =
            if (keepPreviousLibraryVisible) stableCustomIconPathByAppId else customIconPathByAppId

        val displayedApps =
            remember(visibleInstalledApps, searchQuery) {
                if (searchQuery.isBlank()) {
                    visibleInstalledApps
                } else {
                    visibleInstalledApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }
            }

        LaunchedEffect(
            visibleInstalledApps,
            visibleGogByPseudoId,
            visibleEpicByPseudoId,
            visibleCustomArtworkPathByAppId,
            visibleCustomGridArtworkPathByAppId,
            visibleCustomCarouselArtworkPathByAppId,
            visibleCustomListArtworkPathByAppId,
            cachedShortcuts,
        ) {
            var deletedCustomOverrides = false
            val refs =
                visibleInstalledApps.flatMap { app ->
                    val gogGame = visibleGogByPseudoId[app.id]
                    val epicGame = visibleEpicByPseudoId[app.id]
                    val overriddenSlots =
                        customArtworkOverrideSlots(
                            app = app,
                            gogGame = gogGame,
                            epicGame = epicGame,
                            hasDefaultCustomArt = visibleCustomArtworkPathByAppId[app.id] != null,
                            hasGridCustomArt = visibleCustomGridArtworkPathByAppId[app.id] != null,
                            hasCarouselCustomArt = visibleCustomCarouselArtworkPathByAppId[app.id] != null,
                            hasListCustomArt = visibleCustomListArtworkPathByAppId[app.id] != null,
                            hasHeroCustomArt =
                                findLibraryArtworkShortcut(cachedShortcuts, app, gogGame, epicGame)
                                    ?.hasExistingArtwork(LibraryShortcutArtwork.LibraryArtworkSlot.GAME_CARD.extraKey) == true,
                        )

                    if (overriddenSlots.isNotEmpty()) {
                        val cacheId = artworkCacheId(app, gogGame, epicGame)
                        if (cacheId != null) {
                            val deleted =
                                withContext(Dispatchers.IO) {
                                    StoreArtworkCache.deleteSlots(context, cacheId.store, cacheId.gameId, overriddenSlots)
                                }
                            deletedCustomOverrides = deletedCustomOverrides || deleted
                        }
                    }

                    StoreArtworkCache
                        .libraryRefs(
                            app = app,
                            gogGame = gogGame,
                            epicGame = epicGame,
                        ).filterNot { it.slot in overriddenSlots }
                }
            val cachedAny =
                withContext(Dispatchers.IO) {
                    StoreArtworkCache.cacheAll(context, refs)
                }
            if (cachedAny || deletedCustomOverrides) artworkCacheRefreshKey++
        }

        // The startup bootstrap screen already masks the first frame. Do not
        // force an extra minimum spinner duration here or the library visibly
        // bounces through two loading states on launch.
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
        // Only block the surface while the first library result is unresolved.
        // After that, keep the current content/empty state visible during
        // background refreshes so the UI does not flicker back to a spinner.
        val initialLibraryLoadPending = !libraryLoaded
        val waitingForFirstEmptyStateResolution =
            installedApps.isEmpty() && (processedScanToken !== scanInputToken || awaitingStoreSync || awaitingShortcutScan)
        val showLoading = initialLibraryLoadPending || waitingForFirstEmptyStateResolution
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

        if (visibleInstalledApps.isEmpty()) {
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
            val gogGame = app?.let { visibleGogByPseudoId[it.id] }
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

        // Publish the focused game's hero artwork to drive the immersive background.
        // Prefers a custom Game Card upload (LibraryArtworkSlot.GAME_CARD), then the
        // store-supplied hero, then the regular grid capsule as a last resort.
        // Reloads shortcuts via IO on every refresh signal so freshly uploaded artwork
        // shows up immediately, mirroring LibraryGameLaunchScreen's lookup pattern.
        LaunchedEffect(
            focusIndex,
            displayedApps,
            shortcutRefreshKey,
            libraryRefreshKey,
            artworkCacheRefreshKey,
        ) {
            val app = displayedApps.getOrNull(focusIndex) ?: displayedApps.firstOrNull()
            if (app == null) {
                activity?.immersiveBackgroundRef?.value = null
                return@LaunchedEffect
            }
            val gogGame = visibleGogByPseudoId[app.id]
            val epicGame = visibleEpicByPseudoId[app.id]
            val isCustom = app.id < 0
            val isEpic = app.id >= 2000000000
            val epicId = if (isEpic) app.id - 2000000000 else 0

            val customHeroFile =
                withContext(Dispatchers.IO) {
                    val shortcut =
                        when {
                            gogGame != null ->
                                ContainerManager(context).loadShortcuts().find {
                                    it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == gogGame.id
                                }
                            else -> findLibraryShortcutForGame(ContainerManager(context), app, isCustom, isEpic, epicId)
                        }
                    shortcut
                        ?.getExtra(LibraryShortcutArtwork.LibraryArtworkSlot.GAME_CARD.extraKey)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { java.io.File(it) }
                        ?.takeIf { it.isFile }
                }

            activity?.immersiveBackgroundRef?.value =
                customHeroFile
                    ?: run {
                        val ref =
                            StoreArtworkCache.heroRef(app, gogGame, epicGame)
                                ?: StoreArtworkCache.primaryRef(
                                    app,
                                    gogGame,
                                    epicGame,
                                    useLibraryCapsule = false,
                                    listMode = false,
                                )
                        StoreArtworkCache.imageModel(context, ref)
                    }
        }

        val openSettingsForApp: (Int, SteamApp) -> Unit = { index, app ->
            activity?.libraryFocusIndex?.value = index
            selectedSteamAppId = app.id
            selectedSteamAppName = app.name
            val gogGame = visibleGogByPseudoId[app.id]
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
                    keyOf = { it.id },
                ) { app, index, rowHeight ->
                    GameCapsule(
                        app = app,
                        gogGame = visibleGogByPseudoId[app.id],
                        epicGame = visibleEpicByPseudoId[app.id],
                        iconRefreshKey = iconRefreshKey,
                        artworkCacheRefreshKey = artworkCacheRefreshKey,
                        isFocusedOverride = index == focusIndex,
                        isControllerActive = isControllerConnected,
                        customArtworkPath = visibleCustomGridArtworkPathByAppId[app.id] ?: visibleCustomArtworkPathByAppId[app.id],
                        customIconPath = visibleCustomIconPathByAppId[app.id],
                        onClick = {
                            detailGogGame = visibleGogByPseudoId[app.id]
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
                        gogGame = visibleGogByPseudoId[app.id],
                        epicGame = visibleEpicByPseudoId[app.id],
                        iconRefreshKey = iconRefreshKey,
                        artworkCacheRefreshKey = artworkCacheRefreshKey,
                        isFocusedOverride = isSelected,
                        isControllerActive = isControllerConnected,
                        customArtworkPath = visibleCustomCarouselArtworkPathByAppId[app.id] ?: visibleCustomArtworkPathByAppId[app.id],
                        customIconPath = visibleCustomIconPathByAppId[app.id],
                        onClick = {
                            detailGogGame = visibleGogByPseudoId[app.id]
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
                    keyOf = { it.id },
                ) { app, index, isSelected ->
                    GameCapsule(
                        app = app,
                        gogGame = visibleGogByPseudoId[app.id],
                        epicGame = visibleEpicByPseudoId[app.id],
                        iconRefreshKey = iconRefreshKey,
                        artworkCacheRefreshKey = artworkCacheRefreshKey,
                        isFocusedOverride = isSelected,
                        isControllerActive = isControllerConnected,
                        customArtworkPath = visibleCustomListArtworkPathByAppId[app.id] ?: visibleCustomArtworkPathByAppId[app.id],
                        customIconPath = visibleCustomIconPathByAppId[app.id],
                        onClick = {
                            detailGogGame = visibleGogByPseudoId[app.id]
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
        CloudSaves,
        Uninstall,
    }

    private data class HomeShortcutUiState(
        val shortcut: Shortcut? = null,
        val isPinned: Boolean = false,
    )

    private data class ArtworkCacheId(
        val store: String,
        val gameId: String,
    )

    private data class GameSettingsActionItem(
        val title: String,
        val icon: ImageVector,
        val accentColor: Color = Accent,
        val onClick: () -> Unit,
    )

    @Composable
    private fun LibraryDetailPopupFrame(
        title: String,
        onDismissRequest: () -> Unit,
        wide: Boolean = false,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        val dismissInteractionSource = remember { MutableInteractionSource() }
        val panelInteractionSource = remember { MutableInteractionSource() }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .clickable(
                        interactionSource = dismissInteractionSource,
                        indication = null,
                        onClick = onDismissRequest,
                    ),
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val panelMaxWidth = if (wide) 440.dp else 360.dp
                val panelWidthFraction = if (wide) 0.72f else 0.58f
                val panelMaxHeight = (maxHeight - 16.dp).coerceAtLeast(240.dp)

                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth(panelWidthFraction)
                            .widthIn(max = panelMaxWidth)
                            .heightIn(max = panelMaxHeight)
                            .clickable(
                                interactionSource = panelInteractionSource,
                                indication = null,
                                onClick = {},
                            ),
                    shape = RoundedCornerShape(16.dp),
                    color = CardDark,
                    border = BorderStroke(1.dp, CardBorder),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                ) {
                    Column {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.common_ui_close),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GameSettingsDialogFrame(
        title: String,
        onDismissRequest: () -> Unit,
        wide: Boolean = false,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                contentAlignment = Alignment.Center,
            ) {
                val widthModifier =
                    if (wide) {
                        Modifier.widthIn(min = 320.dp, max = (maxWidth - 32.dp).coerceAtMost(560.dp))
                    } else {
                        Modifier.widthIn(min = 200.dp, max = 280.dp)
                    }
                val maxContentHeight = (maxHeight - 48.dp).coerceAtLeast(320.dp)
                Surface(
                    modifier = widthModifier.heightIn(max = maxContentHeight),
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
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                        ) {
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
        val artworkRefreshListener =
            remember(app.id, isCustom, isEpic, epicId) {
                object : EventDispatcher.JavaEventListener {
                    override fun onEvent(event: Any) {
                        if (event is AndroidEvent.LibraryArtworkChanged) {
                            shortcutRefreshKey++
                        }
                    }
                }
            }
        DisposableEffect(artworkRefreshListener) {
            PluviaApp.events.onJava(AndroidEvent.LibraryArtworkChanged::class, artworkRefreshListener)
            onDispose {
                PluviaApp.events.offJava(AndroidEvent.LibraryArtworkChanged::class, artworkRefreshListener)
            }
        }
        val hasPinnedShortcut = pinnedShortcutOverride ?: homeShortcutState.isPinned

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

                            val goldbergSaves = java.io.File(SteamService.getAppDirPath(app.id), "steam_settings/saves")
                            if (goldbergSaves.exists() && goldbergSaves.isDirectory) {
                                dirsToZip.add(goldbergSaves)
                            }

                            if (shortcut != null) {
                                val prefixDir = java.io.File(shortcut.container.getRootDir(), ".wine/drive_c/users/xuser")
                                val docs = java.io.File(prefixDir, "Documents")
                                val savedGames = java.io.File(prefixDir, "Saved Games")
                                val appData = java.io.File(prefixDir, "AppData")
                                if (docs.exists()) dirsToZip.add(docs)
                                if (savedGames.exists()) dirsToZip.add(savedGames)
                                if (appData.exists()) dirsToZip.add(appData)
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
                                val baseName = dir.name
                                zos.putNextEntry(java.util.zip.ZipEntry("$baseName/"))
                                zos.closeEntry()
                                zipDir(dir, baseName)
                            }

                            zos.close()
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
                                    context,
                                    R.string.saves_import_export_exported,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
                                    context,
                                    R.string.saves_import_export_imported,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
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
            wide = currentTab == GameSettingsScreen.CloudSaves,
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
                                                com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
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

                GameSettingsScreen.CloudSaves -> {
                    var isWorking by remember { mutableStateOf(false) }
                    val shortcut =
                        remember(app.id, epicId, isCustom, isEpic) {
                            findLibraryShortcutForGame(ContainerManager(context), app, isCustom, isEpic, epicId)
                        }
                    var cloudSyncEnabled by remember(shortcut?.file?.absolutePath) {
                        mutableStateOf(isShortcutCloudSyncEnabled(shortcut))
                    }
                    var offlineModeEnabled by remember(shortcut?.file?.absolutePath) {
                        mutableStateOf(isShortcutOfflineMode(shortcut))
                    }

                    val gameSource =
                        when {
                            isEpic -> GameSaveBackupManager.GameSource.EPIC
                            isCustom -> GameSaveBackupManager.GameSource.CUSTOM
                            else -> GameSaveBackupManager.GameSource.STEAM
                        }
                    val gameIdStr =
                        when {
                            isEpic -> epicId.toString()
                            isCustom -> shortcut?.let { GameSaveBackupManager.customGameId(it) } ?: app.name
                            else -> app.id.toString()
                        }
                    val providerLabel =
                        when (gameSource) {
                            GameSaveBackupManager.GameSource.EPIC ->
                                stringResource(R.string.preloader_platform_epic)
                            GameSaveBackupManager.GameSource.CUSTOM ->
                                stringResource(R.string.preloader_platform_custom)
                            else ->
                                stringResource(R.string.preloader_platform_steam)
                        }

                    CloudSavesContent(
                        activity = this@UnifiedActivity,
                        isWorking = isWorking,
                        cloudSyncEnabled = cloudSyncEnabled,
                        offlineModeEnabled = offlineModeEnabled,
                        gameSource = gameSource,
                        gameId = gameIdStr,
                        gameName = app.name,
                        shortcut = shortcut,
                        onCloudSyncToggle = { enabled ->
                            cloudSyncEnabled = enabled
                            setShortcutCloudSyncEnabled(shortcut, enabled)
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                if (enabled) {
                                    context.getString(R.string.cloud_sync_enabled_summary)
                                } else {
                                    context.getString(R.string.cloud_sync_disabled_summary)
                                },
                                android.widget.Toast.LENGTH_SHORT,
                            )
                        },
                        onOfflineModeToggle = { enabled ->
                            offlineModeEnabled = enabled
                            setShortcutOfflineMode(shortcut, enabled)
                        },
                        onSyncFromCloud = {
                            if (!isWorking) {
                                isWorking = true
                                scope.launch(Dispatchers.IO) {
                                    val ok =
                                        CloudSyncHelper.downloadCloudSaves(
                                            context,
                                            gameSource,
                                            gameIdStr,
                                            shortcut,
                                        )
                                    withContext(Dispatchers.Main) {
                                        isWorking = false
                                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                                            context,
                                            if (ok) {
                                                context.getString(
                                                    R.string.cloud_saves_sync_from_provider_success,
                                                    providerLabel,
                                                )
                                            } else {
                                                context.getString(
                                                    R.string.cloud_saves_sync_from_provider_failed,
                                                    providerLabel,
                                                )
                                            },
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                    }
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
                                    PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(app.id))
                                    withContext(Dispatchers.Main) {
                                        com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                                context,
                                                getString(R.string.library_games_game_uninstalled, app.name),
                                                android.widget.Toast.LENGTH_SHORT,
                                            )
                                        } else {
                                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                                context,
                                                getString(
                                                    R.string.library_games_failed_to_uninstall_reason,
                                                    result.exceptionOrNull()?.message
                                                        ?: getString(R.string.common_ui_unknown_error),
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
                                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                                            context,
                                            getString(R.string.library_games_game_uninstalled, app.name),
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                    } else {
                                        com.winlator.cmod.shared.ui.toast.WinToast.show(
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
            wide = currentTab == GameSettingsScreen.CloudSaves,
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
                                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                    var offlineModeEnabled by remember(shortcut?.file?.absolutePath) {
                        mutableStateOf(isShortcutOfflineMode(shortcut))
                    }

                    val gogProviderLabel = stringResource(R.string.preloader_platform_gog)

                    CloudSavesContent(
                        activity = this@UnifiedActivity,
                        isWorking = isWorking,
                        cloudSyncEnabled = cloudSyncEnabled,
                        offlineModeEnabled = offlineModeEnabled,
                        gameSource = GameSaveBackupManager.GameSource.GOG,
                        gameId = app.id,
                        gameName = app.title,
                        shortcut = shortcut,
                        onCloudSyncToggle = { enabled ->
                            cloudSyncEnabled = enabled
                            setShortcutCloudSyncEnabled(shortcut, enabled)
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                if (enabled) {
                                    context.getString(R.string.cloud_sync_enabled_summary)
                                } else {
                                    context.getString(R.string.cloud_sync_disabled_summary)
                                },
                                android.widget.Toast.LENGTH_SHORT,
                            )
                        },
                        onOfflineModeToggle = { enabled ->
                            offlineModeEnabled = enabled
                            setShortcutOfflineMode(shortcut, enabled)
                        },
                        onSyncFromCloud = {
                            if (!isWorking) {
                                isWorking = true
                                scope.launch(Dispatchers.IO) {
                                    val ok =
                                        CloudSyncHelper.downloadCloudSaves(
                                            context,
                                            GameSaveBackupManager.GameSource.GOG,
                                            app.id,
                                            shortcut,
                                        )
                                    withContext(Dispatchers.Main) {
                                        isWorking = false
                                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                                            context,
                                            if (ok) {
                                                context.getString(
                                                    R.string.cloud_saves_sync_from_provider_success,
                                                    gogProviderLabel,
                                                )
                                            } else {
                                                context.getString(
                                                    R.string.cloud_saves_sync_from_provider_failed,
                                                    gogProviderLabel,
                                                )
                                            },
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                    }
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
                                val result = GOGService.deleteGame(
                                    context,
                                    LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.feature.stores.steam.enums.GameSource.GOG),
                                )
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                                            context,
                                            getString(R.string.library_games_game_uninstalled, app.title),
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                    } else {
                                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                                            context,
                                            getString(
                                                R.string.library_games_failed_to_uninstall_reason,
                                                result.exceptionOrNull()?.message
                                                    ?: getString(R.string.common_ui_unknown_error),
                                            ),
                                            android.widget.Toast.LENGTH_LONG,
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

    // Library Game Detail Dialog

    private enum class LibraryDetailScreen { Main, Shortcut, CloudSaves, Uninstall }

    private enum class LibraryDetailPopup { CloudSaves }

    @Composable
    private fun LibraryGameDetailDialog(
        app: SteamApp,
        gogGame: GOGGame? = null,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var currentScreen by remember { mutableStateOf(LibraryDetailScreen.Main) }
        var activePopup by remember { mutableStateOf<LibraryDetailPopup?>(null) }
        var shortcutRefreshKey by remember(app.id, gogGame?.id) { mutableStateOf(0) }
        var pinnedShortcutOverride by remember(app.id, gogGame?.id) { mutableStateOf<Boolean?>(null) }
        var showWorkshopDialog by remember(app.id) { mutableStateOf(false) }

        val isCustom = app.id < 0
        val isEpic = app.id >= 2000000000
        val isGog = gogGame != null
        val epicId = if (isEpic) app.id - 2000000000 else 0

        val libraryDownloadRecords by com.winlator.cmod.app.service.download.DownloadCoordinator.records.collectAsState(
            initial = com.winlator.cmod.app.service.download.DownloadCoordinator.snapshotRecords(),
        )
        val hasBlockingSteamDownloadForLibrary =
            !isCustom && !isEpic && !isGog &&
                libraryDownloadRecords.any {
                    it.store == com.winlator.cmod.app.db.download.DownloadRecord.STORE_STEAM &&
                        it.storeGameId == app.id.toString() &&
                        it.status in setOf(
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_QUEUED,
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_DOWNLOADING,
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_PAUSED,
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_FAILED,
                        )
                }
        val hasBlockingEpicDownloadForLibrary =
            isEpic &&
                libraryDownloadRecords.any {
                    it.store == com.winlator.cmod.app.db.download.DownloadRecord.STORE_EPIC &&
                        it.storeGameId == epicId.toString() &&
                        it.status in setOf(
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_QUEUED,
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_DOWNLOADING,
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_PAUSED,
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_FAILED,
                        )
                }
        val hasBlockingGogDownloadForLibrary =
            isGog &&
                libraryDownloadRecords.any {
                    it.store == com.winlator.cmod.app.db.download.DownloadRecord.STORE_GOG &&
                        it.storeGameId == gogGame?.id &&
                        it.status in setOf(
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_QUEUED,
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_DOWNLOADING,
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_PAUSED,
                            com.winlator.cmod.app.db.download.DownloadRecord.STATUS_FAILED,
                        )
                }

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
        val artworkRefreshListener =
            remember(app.id, gogGame?.id) {
                object : EventDispatcher.JavaEventListener {
                    override fun onEvent(event: Any) {
                        if (event is AndroidEvent.LibraryArtworkChanged) {
                            shortcutRefreshKey++
                        }
                    }
                }
            }
        DisposableEffect(artworkRefreshListener) {
            PluviaApp.events.onJava(AndroidEvent.LibraryArtworkChanged::class, artworkRefreshListener)
            onDispose {
                PluviaApp.events.offJava(AndroidEvent.LibraryArtworkChanged::class, artworkRefreshListener)
            }
        }
        val hasPinnedShortcut = pinnedShortcutOverride ?: homeShortcutState.isPinned

        BackHandler(enabled = activePopup != null) {
            activePopup = null
        }

        // Hero image
        val customHeroImageFile =
            homeShortcutState.shortcut
                ?.getExtra("customLibraryHeroArtPath")
                ?.takeIf { it.isNotBlank() }
                ?.let { java.io.File(it) }
                ?.takeIf { it.exists() }
        val customHeroImageCacheKey =
            customHeroImageFile?.let {
                "library_custom_hero:${it.absolutePath}:${it.lastModified()}"
            }
        val heroImageUrl: Any? =
            customHeroImageFile ?: when {
                isGog -> {
                    StoreArtworkCache.imageModel(context, StoreArtworkCache.gogHeroRef(gogGame!!))
                }

                isEpic -> {
                    epicGame?.let { StoreArtworkCache.imageModel(context, StoreArtworkCache.epicHeroRef(it)) }
                }

                isCustom -> {
                    val customCoverArt =
                        homeShortcutState.shortcut
                            ?.getExtra("customCoverArtPath")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { java.io.File(it) }
                            ?.takeIf { it.exists() }
                    customCoverArt ?: run {
                        val safeName = app.name.replace("/", "_").replace("\\", "_")
                        val iconFile = java.io.File(context.filesDir, "custom_icons/$safeName.png")
                        if (iconFile.exists()) iconFile else null
                    }
                }

                else -> {
                    val heroUrl = app.getHeroUrl()
                    StoreArtworkCache.imageModel(context, StoreArtworkCache.steamRef(app, "hero", heroUrl))
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
                    ).distinctBy { it.trim().lowercase() }.joinToString(" • ")
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
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
                                    context,
                                    R.string.saves_import_export_exported,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
                                    context,
                                    R.string.saves_import_export_imported,
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
                                    context,
                                    getString(R.string.saves_import_export_imported_failed, e.message),
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                        }
                    }
                }
            }

        val uninstallGame: () -> Unit = {
            if (isGog) {
                scope.launch(Dispatchers.IO) {
                    val result = GOGService.deleteGame(
                        context,
                        LibraryItem(
                            "GOG_${gogGame!!.id}",
                            gogGame.title,
                            com.winlator.cmod.feature.stores.steam.enums.GameSource.GOG,
                        ),
                    )
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                getString(R.string.library_games_game_uninstalled, app.name),
                                android.widget.Toast.LENGTH_SHORT,
                            )
                        } else {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                getString(
                                    R.string.library_games_failed_to_uninstall_reason,
                                    result.exceptionOrNull()?.message ?: getString(R.string.common_ui_unknown_error),
                                ),
                                android.widget.Toast.LENGTH_LONG,
                            )
                        }
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
                        com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                getString(R.string.library_games_game_uninstalled, app.name),
                                android.widget.Toast.LENGTH_SHORT,
                            )
                        } else {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                            context,
                            getString(R.string.library_games_game_uninstalled, app.name),
                            android.widget.Toast.LENGTH_SHORT,
                        )
                    } else {
                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                            context,
                            getString(R.string.library_games_failed_to_uninstall),
                            android.widget.Toast.LENGTH_SHORT,
                        )
                    }
                    onDismissRequest()
                }
            }
        }

        Dialog(
            onDismissRequest = onDismissRequest,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                color = Color.Black,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        val showHero = currentScreen == LibraryDetailScreen.Main
                        val subScreenTitle =
                            when (currentScreen) {
                                LibraryDetailScreen.Shortcut -> stringResource(R.string.common_ui_shortcut)
                                LibraryDetailScreen.Uninstall ->
                                    stringResource(
                                        if (isCustom) R.string.common_ui_remove else R.string.common_ui_uninstall,
                                    )
                                else -> ""
                            }
                        // Sub-screens get a compact title bar. The main launch view owns the full
                        // screen and draws artwork edge-to-edge in its content branch.
                        if (!showHero) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceDark)
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { currentScreen = LibraryDetailScreen.Main }) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = stringResource(R.string.common_ui_back),
                                        tint = TextPrimary,
                                    )
                                }
                                Text(
                                    subScreenTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                                )
                                Text(
                                    app.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = 16.dp),
                                )
                            }
                            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                        }

                        // Bottom content
                        when (currentScreen) {
                            LibraryDetailScreen.Main -> {
                                // Lock Play while VERIFY / UPDATE is rewriting depots in place
                                // for this game — launching mid-write can corrupt the install.
                                val activePlayBlockingTask =
                                    if (isCustom) {
                                        null
                                    } else if (isGog) {
                                        val gogIdStr = gogGame!!.id
                                        libraryDownloadRecords.firstOrNull { rec ->
                                            rec.store == com.winlator.cmod.app.db.download
                                                .DownloadRecord.STORE_GOG &&
                                                rec.storeGameId == gogIdStr &&
                                                rec.status ==
                                                com.winlator.cmod.app.db.download
                                                    .DownloadRecord.STATUS_DOWNLOADING &&
                                                (
                                                    rec.taskType ==
                                                        com.winlator.cmod.app.db.download
                                                            .DownloadRecord.TASK_VERIFY ||
                                                        rec.taskType ==
                                                            com.winlator.cmod.app.db.download
                                                                .DownloadRecord.TASK_UPDATE
                                                )
                                        }?.taskType
                                    } else if (isEpic) {
                                        val appIdStr = epicId.toString()
                                        libraryDownloadRecords.firstOrNull { rec ->
                                            rec.store == com.winlator.cmod.app.db.download
                                                .DownloadRecord.STORE_EPIC &&
                                                rec.storeGameId == appIdStr &&
                                                rec.status ==
                                                com.winlator.cmod.app.db.download
                                                    .DownloadRecord.STATUS_DOWNLOADING &&
                                                (
                                                    rec.taskType ==
                                                        com.winlator.cmod.app.db.download
                                                            .DownloadRecord.TASK_VERIFY ||
                                                        rec.taskType ==
                                                            com.winlator.cmod.app.db.download
                                                                .DownloadRecord.TASK_UPDATE
                                                )
                                        }?.taskType
                                    } else {
                                        val appIdStr = app.id.toString()
                                        libraryDownloadRecords.firstOrNull { rec ->
                                            rec.store == com.winlator.cmod.app.db.download
                                                .DownloadRecord.STORE_STEAM &&
                                                rec.storeGameId == appIdStr &&
                                                rec.status ==
                                                com.winlator.cmod.app.db.download
                                                    .DownloadRecord.STATUS_DOWNLOADING &&
                                                (
                                                    rec.taskType ==
                                                        com.winlator.cmod.app.db.download
                                                            .DownloadRecord.TASK_VERIFY ||
                                                        rec.taskType ==
                                                            com.winlator.cmod.app.db.download
                                                                .DownloadRecord.TASK_UPDATE
                                                )
                                        }?.taskType
                                    }
                                val playEnabled = activePlayBlockingTask == null
                                val playDisabledLabel =
                                    when (activePlayBlockingTask) {
                                        com.winlator.cmod.app.db.download.DownloadRecord.TASK_VERIFY ->
                                            stringResource(R.string.downloads_queue_phase_verifying)
                                        com.winlator.cmod.app.db.download.DownloadRecord.TASK_UPDATE ->
                                            stringResource(R.string.downloads_queue_phase_updating)
                                        else -> null
                                    }
                                val launchAppName =
                                    when {
                                        isEpic -> epicGame?.title?.takeIf { it.isNotBlank() } ?: app.name
                                        isGog -> gogGame?.title?.takeIf { it.isNotBlank() } ?: app.name
                                        else -> app.name
                                    }
                                LibraryGameLaunchScreen(
                                    appName = launchAppName,
                                    subtitle = subtitle,
                                    sourceLabel = sourceLabel,
                                    heroImageUrl = heroImageUrl,
                                    customHeroImageCacheKey = customHeroImageCacheKey,
                                    releaseDateEpochSeconds = app.releaseDate,
                                    totalPlaytimeMillis = totalPlaytime,
                                    playCount = playCount,
                                    lastPlayedMillis = lastPlayed,
                                    installSizeText = installSizeText,
                                    isCustom = isCustom,
                                    hasPinnedShortcut = hasPinnedShortcut,
                                    playEnabled = playEnabled,
                                    playDisabledLabel = playDisabledLabel,
                                    onBack = onDismissRequest,
                                    onPlay = {
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
                                    },
                                    onSettings = {
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
                                    onShortcut = {
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
                                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                                    onCloudSaves = { activePopup = LibraryDetailPopup.CloudSaves },
                                    onUninstall = uninstallGame,
                                    // Store source tag actions. Steam exposes verify/update/workshop;
                                    // Epic and GOG expose verify/update for installed games.
                                    steamMenuEnabled = !isCustom &&
                                        (!isEpic || epicGame?.isInstalled == true) &&
                                        (!isGog || gogGame?.isInstalled == true),
                                    showVerifyFiles = !isCustom &&
                                        (!isEpic || epicGame?.isInstalled == true) &&
                                        (!isGog || gogGame?.isInstalled == true),
                                    showCheckForUpdate = !isCustom &&
                                        (!isEpic || epicGame?.isInstalled == true) &&
                                        (!isGog || gogGame?.isInstalled == true),
                                    showWorkshop = !isEpic && !isGog,
                                    areSteamActionsEnabled =
                                        when {
                                            isEpic -> !hasBlockingEpicDownloadForLibrary
                                            isGog -> !hasBlockingGogDownloadForLibrary
                                            else -> !hasBlockingSteamDownloadForLibrary
                                        },
                                    onVerifyFiles = {
                                        context.runIfOnlineOrToast {
                                            scope.launch {
                                                val started =
                                                    withContext(Dispatchers.IO) {
                                                        when {
                                                            isEpic -> EpicService.verifyGameFiles(context, epicId)
                                                            isGog -> GOGService.verifyGameFiles(context, gogGame!!.id)
                                                            else -> SteamService.downloadAppForVerify(app.id)
                                                        }
                                                    }
                                                if (started != null) {
                                                    // Hand off to the activity-root host so the
                                                    // pop-up + completion notice outlive this dialog.
                                                    showTaskProgressPopup(
                                                        started,
                                                        if (isGog) gogGame!!.title else app.name,
                                                        getString(R.string.store_game_verify_complete),
                                                        getString(R.string.store_game_verify_failed_notice),
                                                        completeAsToast = true,
                                                    )
                                                }
                                                if (started == null) {
                                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                                        context,
                                                        getString(R.string.store_game_download_already_active),
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onCheckForUpdate = {
                                        when {
                                            isEpic -> startEpicUpdateCheck(epicId, app.name)
                                            isGog -> startGogUpdateCheck(gogGame!!.id, gogGame.title)
                                            else -> startUpdateCheck(app.id, app.name)
                                        }
                                    },
                                    onWorkshop = { if (!isEpic && !isGog) showWorkshopDialog = true },
                                )
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
                                                com.winlator.cmod.shared.ui.toast.WinToast.show(
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

                            LibraryDetailScreen.CloudSaves -> {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                ) {
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
                                var offlineModeEnabled by remember(detailShortcut?.file?.absolutePath) {
                                    mutableStateOf(isShortcutOfflineMode(detailShortcut))
                                }

                                val detailProviderLabel =
                                    when (detailGameSource) {
                                        GameSaveBackupManager.GameSource.GOG ->
                                            stringResource(R.string.preloader_platform_gog)
                                        GameSaveBackupManager.GameSource.EPIC ->
                                            stringResource(R.string.preloader_platform_epic)
                                        GameSaveBackupManager.GameSource.CUSTOM ->
                                            stringResource(R.string.preloader_platform_custom)
                                        GameSaveBackupManager.GameSource.STEAM ->
                                            stringResource(R.string.preloader_platform_steam)
                                    }

                                CloudSavesContent(
                                    activity = this@UnifiedActivity,
                                    isWorking = isWorking,
                                    cloudSyncEnabled = cloudSyncEnabled,
                                    offlineModeEnabled = offlineModeEnabled,
                                    gameSource = detailGameSource,
                                    gameId = detailGameId,
                                    gameName = app.name,
                                    shortcut = detailShortcut,
                                    onCloudSyncToggle = { enabled ->
                                        cloudSyncEnabled = enabled
                                        setShortcutCloudSyncEnabled(detailShortcut, enabled)
                                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                                            context,
                                            if (enabled) {
                                                context.getString(R.string.cloud_sync_enabled_summary)
                                            } else {
                                                context.getString(R.string.cloud_sync_disabled_summary)
                                            },
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                    },
                                    onOfflineModeToggle = { enabled ->
                                        offlineModeEnabled = enabled
                                        setShortcutOfflineMode(detailShortcut, enabled)
                                    },
                                    onSyncFromCloud = {
                                        if (!isWorking) {
                                            isWorking = true
                                            scope.launch(Dispatchers.IO) {
                                                val ok =
                                                    CloudSyncHelper.downloadCloudSaves(
                                                        context,
                                                        detailGameSource,
                                                        detailGameId,
                                                        detailShortcut,
                                                    )
                                                withContext(Dispatchers.Main) {
                                                    isWorking = false
                                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                                        context,
                                                        if (ok) {
                                                            context.getString(
                                                                R.string.cloud_saves_sync_from_provider_success,
                                                                detailProviderLabel,
                                                            )
                                                        } else {
                                                            context.getString(
                                                                R.string.cloud_saves_sync_from_provider_failed,
                                                                detailProviderLabel,
                                                            )
                                                        },
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    showBottomBack = false,
                                    onBack = { currentScreen = LibraryDetailScreen.Main },
                                )
                                }
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
                                        onConfirm = uninstallGame,
                                        onCancel = { currentScreen = LibraryDetailScreen.Main },
                                    )
                                }
                            }
                        }
                    }

                    activePopup?.let { popup ->
                        LibraryDetailPopupFrame(
                            title =
                                when (popup) {
                                    LibraryDetailPopup.CloudSaves -> stringResource(R.string.cloud_saves_title)
                                },
                            wide = popup == LibraryDetailPopup.CloudSaves,
                            onDismissRequest = { activePopup = null },
                        ) {
                            when (popup) {
                                LibraryDetailPopup.CloudSaves -> {
                                    var isWorking by remember { mutableStateOf(false) }

                                    val detailGameSource =
                                        when {
                                            isGog -> GameSaveBackupManager.GameSource.GOG
                                            isEpic -> GameSaveBackupManager.GameSource.EPIC
                                            isCustom -> GameSaveBackupManager.GameSource.CUSTOM
                                            else -> GameSaveBackupManager.GameSource.STEAM
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
                                    val detailGameId =
                                        when {
                                            isGog -> gogGame!!.id
                                            isEpic -> epicId.toString()
                                            isCustom ->
                                                detailShortcut?.let { GameSaveBackupManager.customGameId(it) }
                                                    ?: app.name
                                            else -> app.id.toString()
                                        }
                                    var cloudSyncEnabled by remember(detailShortcut?.file?.absolutePath) {
                                        mutableStateOf(isShortcutCloudSyncEnabled(detailShortcut))
                                    }
                                    var offlineModeEnabled by remember(detailShortcut?.file?.absolutePath) {
                                        mutableStateOf(isShortcutOfflineMode(detailShortcut))
                                    }

                                    val detailProviderLabel =
                                        when (detailGameSource) {
                                            GameSaveBackupManager.GameSource.GOG ->
                                                stringResource(R.string.preloader_platform_gog)
                                            GameSaveBackupManager.GameSource.EPIC ->
                                                stringResource(R.string.preloader_platform_epic)
                                            GameSaveBackupManager.GameSource.CUSTOM ->
                                                stringResource(R.string.preloader_platform_custom)
                                            GameSaveBackupManager.GameSource.STEAM ->
                                                stringResource(R.string.preloader_platform_steam)
                                        }

                                    CloudSavesContent(
                                        activity = this@UnifiedActivity,
                                        isWorking = isWorking,
                                        cloudSyncEnabled = cloudSyncEnabled,
                                        offlineModeEnabled = offlineModeEnabled,
                                        gameSource = detailGameSource,
                                        gameId = detailGameId,
                                        gameName = app.name,
                                        shortcut = detailShortcut,
                                        onCloudSyncToggle = { enabled ->
                                            cloudSyncEnabled = enabled
                                            setShortcutCloudSyncEnabled(detailShortcut, enabled)
                                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                                context,
                                                if (enabled) {
                                                    context.getString(R.string.cloud_sync_enabled_summary)
                                                } else {
                                                    context.getString(R.string.cloud_sync_disabled_summary)
                                                },
                                                android.widget.Toast.LENGTH_SHORT,
                                            )
                                        },
                                        onOfflineModeToggle = { enabled ->
                                            offlineModeEnabled = enabled
                                            setShortcutOfflineMode(detailShortcut, enabled)
                                        },
                                    onSyncFromCloud = {
                                        if (!isWorking) {
                                            isWorking = true
                                                scope.launch(Dispatchers.IO) {
                                                    val ok =
                                                        CloudSyncHelper.downloadCloudSaves(
                                                            context,
                                                            detailGameSource,
                                                            detailGameId,
                                                            detailShortcut,
                                                        )
                                                    withContext(Dispatchers.Main) {
                                                        isWorking = false
                                                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                                                            context,
                                                            if (ok) {
                                                                context.getString(
                                                                    R.string.cloud_saves_sync_from_provider_success,
                                                                    detailProviderLabel,
                                                                )
                                                            } else {
                                                                context.getString(
                                                                    R.string.cloud_saves_sync_from_provider_failed,
                                                                    detailProviderLabel,
                                                                )
                                                            },
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        showTitle = false,
                                        showBottomBack = false,
                                        onBack = { activePopup = null },
                                    )
                                }
                            }
                        }
                    }

                    if (
                        currentScreen != LibraryDetailScreen.Main &&
                        currentScreen != LibraryDetailScreen.CloudSaves
                    ) {
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

                if (showWorkshopDialog) {
                    WorkshopDialog(
                        appId = app.id,
                        gameTitle = app.name,
                        onDismissRequest = { showWorkshopDialog = false },
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
                Text(
                    label,
                    color = tint,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    // Single game capsule for carousel / grid / list
    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    private fun GameCapsule(
        app: SteamApp,
        gogGame: GOGGame? = null,
        epicGame: EpicGame? = null,
        iconRefreshKey: Int = 0,
        artworkCacheRefreshKey: Int = 0,
        isFocusedOverride: Boolean = false,
        isControllerActive: Boolean = false,
        customArtworkPath: String? = null,
        customIconPath: String? = null,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        useLibraryCapsule: Boolean = false,
        listMode: Boolean = false,
        modifier: Modifier = Modifier,
    ) {
        val context = LocalContext.current
        val isCustom = app.id < 0
        val isEpic = app.id >= 2000000000
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
            } else {
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
                customArtworkPath
                    ?.let { java.io.File(it) }

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
                val iconFile = customIconPath?.let { path -> java.io.File(path) }
                if (iconFile != null) {
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
            } else {
                val imageModel =
                    remember(app.id, gogGame, epicGame, useLibraryCapsule, listMode, artworkCacheRefreshKey) {
                        StoreArtworkCache.imageModel(
                            context,
                            StoreArtworkCache.primaryRef(app, gogGame, epicGame, useLibraryCapsule, listMode),
                        )
                    }
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(imageModel)
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
            val heroRef = if (!isCustom && gogGame == null && !isEpic) StoreArtworkCache.heroRef(app, null, null) else null
            val heroModel =
                remember(app.id, heroRef, artworkCacheRefreshKey) {
                    StoreArtworkCache.imageModel(context, heroRef)
                }

            Box(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (isControllerActive && !isFocused) {
                                Modifier.border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            } else {
                                Modifier
                            },
                        ).chasingBorder(
                            isFocused = isFocused,
                            paused = chasingBordersPaused.value || !libraryTabActive.value,
                            cornerRadius = 14.dp,
                        ).background(CardDark, RoundedCornerShape(14.dp))
                        .focusable()
                        .then(clickModifier),
            ) {
                // Hero background layer (falls back to CardDark if image fails)
                if (heroRef != null) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(context)
                                .data(heroModel)
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
                        .then(
                            if (isFocused) {
                                Modifier
                            } else {
                                Modifier.border(1.dp, CardDark, RoundedCornerShape(12.dp))
                            },
                        ).chasingBorder(
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
        epicApps: List<EpicGame>,
        searchQuery: String = "",
        layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID_4,
        onLoginClick: () -> Unit,
    ) {
        val context = LocalContext.current

        if (!isLoggedIn) {
            LoginRequiredScreen("Epic Games", onLoginClick)
            return
        }

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
        val installStateById = rememberEpicInstallStateMap(context, displayedApps)

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
                keyOf = { it.id },
            ) { app, _, _ ->
                EpicStoreCapsule(
                    app,
                    isInstalled = installStateById[app.id] == true,
                    listMode = true,
                    isControllerActive = ControllerHelper.isControllerConnected(),
                ) {
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
                keyOf = { it.id },
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
                        isInstalled = installStateById[app.id] == true,
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
    private fun StoreInstalledBadge(
        modifier: Modifier = Modifier,
        compact: Boolean = false,
        attachedCorner: Boolean = false,
    ) {
        val shape =
            if (attachedCorner) {
                RoundedCornerShape(topStart = 8.dp)
            } else {
                RoundedCornerShape(4.dp)
            }
        Box(
            modifier =
                modifier
                    .background(StatusOnline, shape)
                    .border(1.dp, Color.White.copy(alpha = 0.34f), shape)
                    .padding(
                        start = if (compact) 6.dp else 9.dp,
                        end = if (compact) 6.dp else 9.dp,
                        top = if (compact) 2.dp else 4.dp,
                        bottom = if (compact) 1.dp else 2.dp,
                    ),
        ) {
            Text(
                stringResource(R.string.library_games_installed_badge),
                color = Color(0xFF06140A),
                fontSize = if (compact) 9.sp else 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
                maxLines = 1,
            )
        }
    }

    @Composable
    fun EpicStoreCapsule(
        app: com.winlator.cmod.feature.stores.epic.data.EpicGame,
        isInstalled: Boolean,
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
                        StoreInstalledBadge(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                            compact = true,
                        )
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
                        StoreInstalledBadge(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            attachedCorner = true,
                        )
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
    fun EpicGameManagerDialog(
        app: EpicGame,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val installed = EpicService.isGameInstalled(context, app.id)
        val scope = rememberCoroutineScope()

        var isLoading by remember { mutableStateOf(!installed) }
        var manifestSizes by remember { mutableStateOf<EpicManager.ManifestSizes?>(null) }
        var dlcApps by remember { mutableStateOf<List<EpicGame>>(emptyList()) }
        val selectedDlcIds = remember { mutableStateListOf<Int>() }
        var customPath by remember { mutableStateOf<String?>(null) }
        var showCustomPathWarning by remember { mutableStateOf(false) }
        var isCheckingForUpdate by remember(app.id) { mutableStateOf(false) }
        var updateInfo by remember(app.id) { mutableStateOf<EpicUpdateInfo?>(null) }
        var updateStatusText by remember(app.id) { mutableStateOf<String?>(null) }
        val epicDownloadRecords by com.winlator.cmod.app.service.download.DownloadCoordinator.records.collectAsState(
            initial = com.winlator.cmod.app.service.download.DownloadCoordinator.snapshotRecords(),
        )
        val hasBlockingEpicDownload =
            epicDownloadRecords.any {
                it.store == com.winlator.cmod.app.db.download.DownloadRecord.STORE_EPIC &&
                    it.storeGameId == app.id.toString() &&
                    it.status in setOf(
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_QUEUED,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_DOWNLOADING,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_PAUSED,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_FAILED,
                    )
            }
        val updateActionEnabled = !hasBlockingEpicDownload
        val activeEpicDownloadText = stringResource(R.string.store_game_download_already_active)
        val noUpdateAvailableText = stringResource(R.string.store_game_no_update_available)
        val updateAvailableText = stringResource(R.string.store_game_update_available)
        val updateFailedText = stringResource(R.string.store_game_update_check_failed)

        if (showCustomPathWarning) {
            CustomPathWarningDialog(
                onDismiss = { showCustomPathWarning = false },
                onProceed = {
                    showCustomPathWarning = false
                    DirectoryPickerDialog.show(
                        activity = this@UnifiedActivity,
                        initialPath = customPath ?: EpicConstants.getGameInstallPath(context, app.appName),
                        title = getString(R.string.settings_content_install_directory),
                    ) { path -> customPath = path }
                },
            )
        }

        LaunchedEffect(app.id, installed) {
            if (!installed) {
                val (sizes, sizedDlcs) =
                    withContext(Dispatchers.IO) {
                        val baseSizes = EpicService.fetchManifestSizes(context, app.id)
                        val dlcs = EpicService.getDLCForGameSuspend(app.id)
                        val dlcsWithSizes =
                            dlcs
                                .map { dlc ->
                                    async {
                                        if (dlc.downloadSize > 0L || dlc.installSize > 0L) {
                                            dlc
                                        } else {
                                            val dlcSizes = EpicService.fetchManifestSizes(context, dlc.id)
                                            dlc.copy(
                                                downloadSize = dlcSizes.downloadSize,
                                                installSize = dlcSizes.installSize,
                                            )
                                        }
                                    }
                                }.awaitAll()
                        baseSizes to dlcsWithSizes
                    }
                manifestSizes = sizes
                dlcApps = sizedDlcs
                isLoading = false
            } else {
                dlcApps =
                    withContext(Dispatchers.IO) {
                        EpicService
                            .getDLCForGameSuspend(app.id)
                            .map { dlc ->
                                async {
                                    if (dlc.downloadSize > 0L || dlc.installSize > 0L) {
                                        dlc
                                    } else {
                                        val dlcSizes = EpicService.fetchManifestSizes(context, dlc.id)
                                        dlc.copy(
                                            downloadSize = dlcSizes.downloadSize,
                                            installSize = dlcSizes.installSize,
                                        )
                                    }
                                }
                            }.awaitAll()
                    }
            }
        }

        val baseDownloadSize = manifestSizes?.downloadSize ?: 0L
        val baseInstallSize = manifestSizes?.installSize ?: 0L
        val selectedDlcDownloadBytes =
            dlcApps.filter { it.id in selectedDlcIds }.sumOf { it.downloadSize.coerceAtLeast(0L) }
        val selectedDlcInstallBytes =
            dlcApps.filter { it.id in selectedDlcIds }.sumOf { it.installSize.coerceAtLeast(0L) }
        val totalDownloadSize = baseDownloadSize + selectedDlcDownloadBytes
        val totalInstallSize = baseInstallSize + selectedDlcInstallBytes
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
        val isInstallEnabled = installed || totalInstallSize == 0L || availableBytes >= totalInstallSize
        val installActionEnabled = isInstallEnabled && !hasBlockingEpicDownload
        val installPathDisplay = if (installed) app.installPath else (customPath ?: EpicConstants.defaultEpicGamesPath(context))

        val dlcItems =
            remember(dlcApps) {
                dlcApps.map { dlc ->
                    val size =
                        dlc.downloadSize.takeIf { it > 0L }
                            ?: dlc.installSize
                    StoreDlcItem(id = dlc.id, name = dlc.title, downloadSize = size, isInstalled = dlc.isInstalled)
                }
            }
        val customPathLabel =
            when {
                customPath != null -> stringResource(R.string.common_ui_custom)
                defaultPathSet -> stringResource(R.string.common_ui_already_set)
                else -> stringResource(R.string.common_ui_custom)
            }

        Dialog(
            onDismissRequest = onDismissRequest,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                color = Color.Black,
            ) {
                StoreGameDetailScreen(
                    title = app.title,
                    subtitle =
                        listOfNotNull(
                            app.developer.takeIf { it.isNotBlank() },
                            app.publisher.takeIf {
                                it.isNotBlank() && !it.equals(app.developer, ignoreCase = true)
                            },
                        ).joinToString(" • "),
                    sourceLabel = "Epic Games",
                    heroImageUrl = StoreArtworkCache.imageModel(context, StoreArtworkCache.epicHeroRef(app)),
                    isLoading = isLoading,
                    isInstalled = installed,
                    installPathDisplay = installPathDisplay,
                    downloadSize = totalDownloadSize,
                    installSize = totalInstallSize,
                    availableBytes = availableBytes,
                    isInstallEnabled = isInstallEnabled,
                    isDownloadActionEnabled = installActionEnabled,
                    customPathLabel = customPathLabel,
                    showCustomPath = true,
                    showCloudSync = false,
                    showUninstall = false,
                    showUpdateCheck = installed,
                    isCheckingForUpdate = isCheckingForUpdate,
                    isUpdateAvailable = updateInfo?.hasUpdate == true,
                    updateDownloadSize = updateInfo?.downloadSize ?: 0L,
                    updateStatusText = updateStatusText,
                    isUpdateActionEnabled = updateActionEnabled,
                    showVerifyFiles = installed,
                    areSteamActionsEnabled = !hasBlockingEpicDownload,
                    dlcs = dlcItems,
                    selectedDlcIds = selectedDlcIds.toSet(),
                    onBack = onDismissRequest,
                    onInstall = {
                        if (hasBlockingEpicDownload) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                activeEpicDownloadText,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            return@StoreGameDetailScreen
                        }
                        val installPath =
                            if (customPath != null) {
                                val sanitizedTitle = app.title.replace(Regex("[^a-zA-Z0-9 \\-_]"), "").trim()
                                java.io.File(customPath!!, sanitizedTitle).absolutePath
                            } else {
                                EpicConstants.getGameInstallPath(context, app.title)
                            }
                        context.runIfOnlineOrToast {
                            EpicService.downloadGame(context, app.id, selectedDlcIds.toList(), installPath, "en-US")
                            onDismissRequest()
                        }
                    },
                    onCloudSync = {
                        scope.launch(Dispatchers.IO) {
                            EpicCloudSavesManager.syncCloudSaves(context, app.id, "auto")
                        }
                        onDismissRequest()
                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                            context,
                            context.getString(R.string.google_cloud_sync_started),
                            android.widget.Toast.LENGTH_SHORT,
                        )
                    },
                    onVerifyFiles = {
                        if (hasBlockingEpicDownload) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                activeEpicDownloadText,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            return@StoreGameDetailScreen
                        }
                        context.runIfOnlineOrToast {
                            scope.launch {
                                val started =
                                    withContext(Dispatchers.IO) {
                                        EpicService.verifyGameFiles(context, app.id)
                                    }
                                if (started != null) {
                                    showTaskProgressPopup(
                                        started,
                                        app.title,
                                        getString(R.string.store_game_verify_complete),
                                        getString(R.string.store_game_verify_failed_notice),
                                        completeAsToast = true,
                                    )
                                } else {
                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                        context,
                                        activeEpicDownloadText,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                }
                            }
                        }
                    },
                    onCheckForUpdate = {
                        if (hasBlockingEpicDownload) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                activeEpicDownloadText,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            return@StoreGameDetailScreen
                        }
                        context.runIfOnlineOrToast {
                            scope.launch {
                                isCheckingForUpdate = true
                                updateStatusText = null
                                val latest =
                                    withContext(Dispatchers.IO) {
                                        EpicService.checkForGameUpdate(context, app.id)
                                    }
                                updateInfo = latest
                                updateStatusText =
                                    when {
                                        latest.hasUpdate -> updateAvailableText
                                        latest.message != null -> updateFailedText
                                        else -> null
                                    }
                                isCheckingForUpdate = false
                                if (!latest.hasUpdate && latest.message == null) {
                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                        context,
                                        noUpdateAvailableText,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                }
                            }
                        }
                    },
                    onDownloadUpdate = {
                        if (!updateActionEnabled || updateInfo?.hasUpdate != true) return@StoreGameDetailScreen
                        context.runIfOnlineOrToast {
                            scope.launch {
                                val latest =
                                    withContext(Dispatchers.IO) {
                                        EpicService.checkForGameUpdate(context, app.id)
                                    }
                                updateInfo = latest
                                updateStatusText =
                                    when {
                                        latest.hasUpdate -> updateAvailableText
                                        latest.message != null -> updateFailedText
                                        else -> null
                                    }
                                if (!latest.hasUpdate) {
                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                        context,
                                        noUpdateAvailableText,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                    return@launch
                                }
                                val started =
                                    withContext(Dispatchers.IO) {
                                        EpicService.updateGameFiles(context, app.id)
                                    }
                                if (started != null) {
                                    onDismissRequest()
                                } else {
                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                        context,
                                        activeEpicDownloadText,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                }
                            }
                        }
                    },
                    onUninstall = {
                        scope.launch(Dispatchers.IO) {
                            val result = EpicService.deleteGame(context, app.id)
                            withContext(Dispatchers.Main) {
                                if (!result.isSuccess) {
                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                        context,
                                        getString(
                                            R.string.library_games_failed_to_uninstall_reason,
                                            result.exceptionOrNull()?.message
                                                ?: getString(R.string.common_ui_unknown_error),
                                        ),
                                        android.widget.Toast.LENGTH_LONG,
                                    )
                                }
                                onDismissRequest()
                            }
                        }
                    },
                    onCustomPath = {
                        if (customPath == null && defaultPathSet) {
                            showCustomPathWarning = true
                        } else {
                            DirectoryPickerDialog.show(
                                activity = this@UnifiedActivity,
                                initialPath = customPath ?: EpicConstants.getGameInstallPath(context, app.appName),
                                title = getString(R.string.settings_content_install_directory),
                            ) { path -> customPath = path }
                        }
                    },
                    onToggleDlc = { id ->
                        if (selectedDlcIds.contains(id)) {
                            selectedDlcIds.remove(id)
                        } else {
                            selectedDlcIds.add(id)
                        }
                    },
                    onToggleSelectAllDlcs = {
                        val all = dlcItems.isNotEmpty() && dlcItems.all { it.id in selectedDlcIds }
                        if (all) {
                            selectedDlcIds.clear()
                        } else {
                            dlcItems.forEach { if (it.id !in selectedDlcIds) selectedDlcIds.add(it.id) }
                        }
                    },
                )
            }
        }
    }

    @Composable
    fun GOGStoreTab(
        isLoggedIn: Boolean,
        gogApps: List<GOGGame>,
        searchQuery: String = "",
        layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID_4,
        onLoginClick: () -> Unit,
    ) {
        if (!isLoggedIn) {
            LoginRequiredScreen("GOG", onLoginClick)
            return
        }

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
        val installStateById = rememberGogInstallStateMap(displayedApps)

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
                keyOf = { it.id },
            ) { app, _, _ ->
                val isInstalled = installStateById[app.id] == true
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
                            StoreInstalledBadge(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                compact = true,
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
                keyOf = { it.id },
            ) { app, index, rowHeight ->
                val isInstalled = installStateById[app.id] == true
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
                            StoreInstalledBadge(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                attachedCorner = true,
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
        val installed = GOGService.isGameInstalled(app.id)
        val scope = rememberCoroutineScope()
        var isLoading by remember(app.id) { mutableStateOf(true) }
        var selectedManifestSizes by remember(app.id) { mutableStateOf(GOGManifestSizes()) }
        var dlcSizes by remember(app.id) { mutableStateOf<Map<Int, GOGManifestSizes>>(emptyMap()) }
        var customPath by remember { mutableStateOf<String?>(null) }
        var showCustomPathWarning by remember { mutableStateOf(false) }
        var dlcApps by remember(app.id) { mutableStateOf<List<GOGDlcInfo>>(emptyList()) }
        val selectedDlcIds = remember(app.id) { mutableStateListOf<Int>() }
        var isCheckingForGogUpdate by remember(app.id) { mutableStateOf(false) }
        var gogUpdateInfo by remember(app.id) { mutableStateOf<GOGUpdateInfo?>(null) }
        var gogUpdateStatusText by remember(app.id) { mutableStateOf<String?>(null) }
        val gogDownloadRecords by com.winlator.cmod.app.service.download.DownloadCoordinator.records.collectAsState(
            initial = com.winlator.cmod.app.service.download.DownloadCoordinator.snapshotRecords(),
        )
        val hasBlockingGogDownload =
            gogDownloadRecords.any {
                it.store == com.winlator.cmod.app.db.download.DownloadRecord.STORE_GOG &&
                    it.storeGameId == app.id &&
                    it.status in setOf(
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_QUEUED,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_DOWNLOADING,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_PAUSED,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_FAILED,
                    )
            }
        val gogUpdateActionEnabled = !hasBlockingGogDownload
        val activeGogDownloadText = stringResource(R.string.store_game_download_already_active)
        val gogNoUpdateAvailableText = stringResource(R.string.store_game_no_update_available)
        val gogUpdateAvailableText = stringResource(R.string.store_game_update_available)
        val gogUpdateFailedText = stringResource(R.string.store_game_update_check_failed)

        if (showCustomPathWarning) {
            CustomPathWarningDialog(
                onDismiss = { showCustomPathWarning = false },
                onProceed = {
                    showCustomPathWarning = false
                    DirectoryPickerDialog.show(
                        activity = this@UnifiedActivity,
                        initialPath = customPath ?: GOGConstants.defaultGOGGamesPath,
                        title = getString(R.string.settings_content_install_directory),
                    ) { path -> customPath = path }
                },
            )
        }

        data class GogInstallLoadData(
            val dlcs: List<GOGDlcInfo>,
            val dlcSizes: Map<Int, GOGManifestSizes>,
            val baseManifestSizes: GOGManifestSizes,
        )

        LaunchedEffect(app.id, PrefManager.containerLanguage) {
            isLoading = true
            val loadData =
                withContext(Dispatchers.IO) {
                    val dlcs = GOGService.getDLCForGameSuspend(app.id, PrefManager.containerLanguage)
                    val perDlcSizes =
                        dlcs.mapNotNull { dlc ->
                            val id = dlc.id.toIntOrNull() ?: return@mapNotNull null
                            id to
                                GOGManifestSizes(
                                    installSize = dlc.installSize,
                                    downloadSize = dlc.downloadSize,
                                )
                        }.toMap()
                    GogInstallLoadData(
                        dlcs = dlcs,
                        dlcSizes = perDlcSizes,
                        baseManifestSizes =
                            GOGService.getInstallableSelectedManifestSizes(
                                app.id,
                                PrefManager.containerLanguage,
                            ),
                    )
                }
            dlcApps = loadData.dlcs
            dlcSizes = loadData.dlcSizes
            selectedManifestSizes = loadData.baseManifestSizes
            selectedDlcIds.clear()
            loadData.dlcs
                .filterNot { it.isInstalled }
                .mapNotNull { it.id.toIntOrNull() }
                .forEach { selectedDlcIds.add(it) }
            isLoading = false
        }

        LaunchedEffect(app.id, PrefManager.containerLanguage, selectedDlcIds.toList()) {
            selectedManifestSizes =
                withContext(Dispatchers.IO) {
                    GOGService.getInstallableSelectedManifestSizes(
                        app.id,
                        PrefManager.containerLanguage,
                        selectedDlcIds.toList(),
                    )
                }
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
            if (installed) {
                app.installPath
            } else if (customPath != null) {
                java.io.File(customPath!!, GOGConstants.getSanitizedGameFolderName(app.title)).absolutePath
            } else {
                GOGConstants.getGameInstallPath(app.title)
            }
        val dlcItems =
            remember(dlcApps, dlcSizes) {
                dlcApps.mapNotNull { dlc ->
                    val id = dlc.id.toIntOrNull() ?: return@mapNotNull null
                    val manifestSize = dlcSizes[id]
                    val size =
                        manifestSize
                            ?.downloadSize
                            ?.takeIf { it > 0L }
                            ?: manifestSize?.installSize?.takeIf { it > 0L }
                            ?: dlc.downloadSize.takeIf { it > 0L }
                            ?: dlc.installSize
                    StoreDlcItem(id = id, name = dlc.title, downloadSize = size, isInstalled = dlc.isInstalled)
                }
            }
        val selectedDlcDownloadSize =
            remember(dlcItems, selectedDlcIds.toList()) {
                dlcItems
                    .filter { !it.isInstalled && it.id in selectedDlcIds }
                    .sumOf { it.downloadSize.coerceAtLeast(0L) }
            }
        val selectedDlcInstallSize =
            remember(dlcSizes, dlcItems, selectedDlcIds.toList()) {
                dlcItems
                    .filter { !it.isInstalled && it.id in selectedDlcIds }
                    .sumOf { dlcSizes[it.id]?.installSize?.takeIf { size -> size > 0L } ?: it.downloadSize.coerceAtLeast(0L) }
            }
        val totalDownloadSize =
            if (installed) {
                selectedDlcDownloadSize
            } else {
                selectedManifestSizes.downloadSize.takeIf { it > 0L }
                    ?: app.downloadSize + selectedDlcDownloadSize
            }
        val totalInstallSize =
            if (installed) {
                selectedDlcInstallSize
            } else {
                selectedManifestSizes.installSize.takeIf { it > 0L }
                    ?: app.installSize.takeIf { it > 0L }
                    ?: totalDownloadSize
            }
        val requiredBytes =
            if (installed) {
                selectedDlcInstallSize.takeIf { it > 0L } ?: selectedDlcDownloadSize
            } else {
                totalInstallSize.takeIf { it > 0L } ?: totalDownloadSize
            }
        val availableBytes =
            try {
                StorageUtils.getAvailableSpace(installRootPath)
            } catch (_: Exception) {
                0L
            }
        val isInstallEnabled = requiredBytes == 0L || availableBytes >= requiredBytes
        val installActionEnabled = isInstallEnabled && !hasBlockingGogDownload
        val customPathLabel =
            when {
                customPath != null -> stringResource(R.string.common_ui_custom)
                defaultPathSet -> stringResource(R.string.common_ui_already_set)
                else -> stringResource(R.string.common_ui_custom)
            }

        Dialog(
            onDismissRequest = onDismissRequest,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                color = Color.Black,
            ) {
                StoreGameDetailScreen(
                    title = app.title,
                    subtitle =
                        listOfNotNull(
                            app.developer.takeIf { it.isNotBlank() },
                            app.publisher.takeIf {
                                it.isNotBlank() && !it.equals(app.developer, ignoreCase = true)
                            },
                        ).joinToString(" • "),
                    sourceLabel = "GOG",
                    heroImageUrl = StoreArtworkCache.imageModel(context, StoreArtworkCache.gogHeroRef(app)),
                    isLoading = isLoading,
                    isInstalled = installed,
                    installPathDisplay = installPathDisplay,
                    downloadSize = totalDownloadSize,
                    installSize = totalInstallSize,
                    availableBytes = availableBytes,
                    isInstallEnabled = isInstallEnabled,
                    isDownloadActionEnabled = installActionEnabled,
                    customPathLabel = customPathLabel,
                    showCustomPath = true,
                    showCloudSync = false,
                    showUninstall = false,
                    showUpdateCheck = installed,
                    isCheckingForUpdate = isCheckingForGogUpdate,
                    isUpdateAvailable = gogUpdateInfo?.hasUpdate == true,
                    updateDownloadSize = gogUpdateInfo?.downloadSize ?: 0L,
                    updateStatusText = gogUpdateStatusText,
                    isUpdateActionEnabled = gogUpdateActionEnabled,
                    showVerifyFiles = installed,
                    areSteamActionsEnabled = !hasBlockingGogDownload,
                    dlcs = dlcItems,
                    selectedDlcIds = selectedDlcIds.toSet(),
                    isDlcSelectionEnabled = installActionEnabled,
                    onBack = onDismissRequest,
                    onInstall = {
                        if (hasBlockingGogDownload) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                getString(R.string.store_game_download_already_active),
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            return@StoreGameDetailScreen
                        }
                        context.runIfOnlineOrToast {
                            GOGService.downloadGame(
                                context,
                                app.id,
                                installPathDisplay,
                                PrefManager.containerLanguage,
                                selectedDlcIds.toList(),
                            )
                            onDismissRequest()
                        }
                    },
                    onCloudSync = {
                        scope.launch(Dispatchers.IO) {
                            GOGService.syncCloudSaves(context, "GOG_${app.id}", "auto")
                        }
                        onDismissRequest()
                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                            context,
                            context.getString(R.string.google_cloud_sync_started),
                            android.widget.Toast.LENGTH_SHORT,
                        )
                    },
                    onVerifyFiles = {
                        if (hasBlockingGogDownload) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                activeGogDownloadText,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            return@StoreGameDetailScreen
                        }
                        context.runIfOnlineOrToast {
                            scope.launch {
                                val started =
                                    withContext(Dispatchers.IO) {
                                        GOGService.verifyGameFiles(context, app.id)
                                    }
                                if (started != null) {
                                    showTaskProgressPopup(
                                        started,
                                        app.title,
                                        getString(R.string.store_game_verify_complete),
                                        getString(R.string.store_game_verify_failed_notice),
                                        completeAsToast = true,
                                    )
                                } else {
                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                        context,
                                        activeGogDownloadText,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                }
                            }
                        }
                    },
                    onCheckForUpdate = {
                        if (hasBlockingGogDownload) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                activeGogDownloadText,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            return@StoreGameDetailScreen
                        }
                        context.runIfOnlineOrToast {
                            scope.launch {
                                isCheckingForGogUpdate = true
                                gogUpdateStatusText = null
                                val latest =
                                    withContext(Dispatchers.IO) {
                                        GOGService.checkForGameUpdate(context, app.id)
                                    }
                                gogUpdateInfo = latest
                                gogUpdateStatusText =
                                    when {
                                        latest.hasUpdate -> gogUpdateAvailableText
                                        latest.message != null -> gogUpdateFailedText
                                        else -> null
                                    }
                                isCheckingForGogUpdate = false
                                if (!latest.hasUpdate && latest.message == null) {
                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                        context,
                                        gogNoUpdateAvailableText,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                }
                            }
                        }
                    },
                    onDownloadUpdate = {
                        if (!gogUpdateActionEnabled || gogUpdateInfo?.hasUpdate != true) return@StoreGameDetailScreen
                        context.runIfOnlineOrToast {
                            scope.launch {
                                val started =
                                    withContext(Dispatchers.IO) {
                                        GOGService.updateGameFiles(context, app.id)
                                    }
                                if (started != null) {
                                    onDismissRequest()
                                } else {
                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                        context,
                                        activeGogDownloadText,
                                        android.widget.Toast.LENGTH_SHORT,
                                    )
                                }
                            }
                        }
                    },
                    onUninstall = {
                        scope.launch(Dispatchers.IO) {
                            val result =
                                GOGService.deleteGame(
                                    context,
                                    LibraryItem(
                                        "GOG_${app.id}",
                                        app.title,
                                        com.winlator.cmod.feature.stores.steam.enums.GameSource.GOG,
                                    ),
                                )
                            withContext(Dispatchers.Main) {
                                if (!result.isSuccess) {
                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                        context,
                                        getString(
                                            R.string.library_games_failed_to_uninstall_reason,
                                            result.exceptionOrNull()?.message
                                                ?: getString(R.string.common_ui_unknown_error),
                                        ),
                                        android.widget.Toast.LENGTH_LONG,
                                    )
                                }
                                onDismissRequest()
                            }
                        }
                    },
                    onCustomPath = {
                        if (customPath == null && defaultPathSet) {
                            showCustomPathWarning = true
                        } else {
                            DirectoryPickerDialog.show(
                                activity = this@UnifiedActivity,
                                initialPath = customPath ?: GOGConstants.defaultGOGGamesPath,
                                title = getString(R.string.settings_content_install_directory),
                            ) { path -> customPath = path }
                        }
                    },
                    onToggleDlc = { id ->
                        if (dlcItems.any { it.id == id && it.isInstalled }) {
                            return@StoreGameDetailScreen
                        }
                        if (selectedDlcIds.contains(id)) {
                            selectedDlcIds.remove(id)
                        } else {
                            selectedDlcIds.add(id)
                        }
                    },
                    onToggleSelectAllDlcs = {
                        val selectableDlcItems = dlcItems.filterNot { it.isInstalled }
                        val all = selectableDlcItems.isNotEmpty() && selectableDlcItems.all { it.id in selectedDlcIds }
                        if (all) {
                            selectedDlcIds.removeAll(selectableDlcItems.map { it.id }.toSet())
                        } else {
                            selectableDlcItems.forEach { if (it.id !in selectedDlcIds) selectedDlcIds.add(it.id) }
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
        val installStateById = rememberSteamInstallStateMap(displayedApps)

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
                keyOf = { it.id },
            ) { app, _, _ ->
                SteamStoreCapsule(
                    app,
                    isInstalled = installStateById[app.id] == true,
                    listMode = true,
                    isControllerActive = ControllerHelper.isControllerConnected(),
                    onClick = {
                        selectedAppForDialog =
                            app
                    },
                )
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
                keyOf = { it.id },
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
                        isInstalled = installStateById[app.id] == true,
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
        isInstalled: Boolean,
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
                            StoreInstalledBadge(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                compact = true,
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
                        StoreInstalledBadge(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            attachedCorner = true,
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

    private data class DownloadCancelRequest(
        val ids: List<String>,
        val isCancelAll: Boolean,
    )

    // Downloads Tab
    @Composable
    fun DownloadsTab(
        selectedId: String?,
        animationsActive: Boolean = true,
        onSelectDownload: (String?) -> Unit,
    ) {
        val downloads = remember { mutableStateListOf<Pair<String, DownloadInfo>>() }
        var tick by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        var cancelWarningRequest by remember { mutableStateOf<DownloadCancelRequest?>(null) }

        val syncDownloads =
            remember(selectedId, onSelectDownload) {
                {
                    val currentDownloads = DownloadService.getAllDownloads()
                    downloads.clear()
                    downloads.addAll(currentDownloads)
                    if (selectedId != null && currentDownloads.none { it.first == selectedId }) {
                        onSelectDownload(null)
                    }
                }
            }
        val latestSyncDownloads by rememberUpdatedState(syncDownloads)

        val downloadStatusListener =
            remember {
                object : EventDispatcher.JavaEventListener {
                    override fun onEvent(event: Any) {
                        if (event is AndroidEvent.DownloadStatusChanged) {
                            scope.launch {
                                latestSyncDownloads()
                            }
                        }
                    }
                }
            }

        DisposableEffect(downloadStatusListener, syncDownloads) {
            syncDownloads()
            PluviaApp.events.onJava(AndroidEvent.DownloadStatusChanged::class, downloadStatusListener)
            onDispose {
                PluviaApp.events.offJava(AndroidEvent.DownloadStatusChanged::class, downloadStatusListener)
            }
        }

        // Re-sync the list whenever the cross-store DownloadCoordinator records change. This
        // is what makes PAUSED records (loaded from DB after app restart) appear in the tab,
        // and what removes COMPLETE/CANCELLED/FAILED rows after Clear.
        LaunchedEffect(syncDownloads) {
            DownloadCoordinator.changes.collect {
                latestSyncDownloads()
            }
        }

        downloads.forEach { (_, info) ->
            LaunchedEffect(info) {
                info.getStatusFlow().collect {
                    tick++
                }
            }
            // Also recompose on status-message changes. Active downloads push
            // a changing message every progress tick, so this is what keeps
            // the byte count / speed / progress bar refreshing live (the
            // phase flow dedups to a single DOWNLOADING emission).
            LaunchedEffect(info) {
                info.getStatusMessageFlow().collect {
                    tick++
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .tabScreenPadding(top = DownloadsHeaderTopPadding),
        ) {
            @Suppress("UNUSED_EXPRESSION")
            tick

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val selectedInfo = downloads.find { it.first == selectedId }?.second
                val selectedStatus = selectedInfo?.getStatusFlow()?.value
                // FAILED is resumable: the dispatcher preserves every breadcrumb
                // on failure, so one click continues from where it left off.
                val isResumable =
                    selectedStatus == DownloadPhase.PAUSED || selectedStatus == DownloadPhase.FAILED
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
                            val s = it.second.getStatusFlow().value
                            s == DownloadPhase.PAUSED || s == DownloadPhase.FAILED
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
                        when {
                            selectedStatus == DownloadPhase.FAILED -> stringResource(R.string.session_drawer_retry)
                            isResumable -> stringResource(R.string.session_drawer_resume)
                            else -> stringResource(R.string.session_drawer_pause)
                        }
                    }

                val cancelLabel =
                    if (selectedId == null) {
                        stringResource(R.string.downloads_queue_cancel_all)
                    } else {
                        stringResource(R.string.common_ui_cancel)
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

                DownloadsQueueButton(
                    label = pauseResumeLabel,
                    accentColor = Accent,
                    onClick = {
                        val isResumeAction =
                            if (selectedId == null) allPausableDownloadsPaused else isResumable
                        val run = {
                            when {
                                selectedId == null && allPausableDownloadsPaused -> DownloadService.resumeAll()
                                selectedId == null -> DownloadService.pauseAll()
                                isResumable -> DownloadService.resumeDownload(selectedId)
                                else -> DownloadService.pauseDownload(selectedId)
                            }
                        }
                        if (isResumeAction) this@UnifiedActivity.runIfOnlineOrToast(run) else run()
                    },
                    enabled = pauseResumeEnabled,
                )

                Box {
                    DownloadsQueueButton(
                        label = cancelLabel,
                        accentColor = DangerRed,
                        onClick = {
                            if (selectedId == null) {
                                cancelWarningRequest =
                                    DownloadCancelRequest(
                                        ids = pausableDownloads.map { it.first },
                                        isCancelAll = true,
                                    )
                            } else {
                                cancelWarningRequest =
                                    DownloadCancelRequest(
                                        ids = listOf(selectedId),
                                        isCancelAll = false,
                                    )
                            }
                        },
                        enabled = cancelEnabled,
                    )

                    cancelWarningRequest?.let { request ->
                        DownloadCancelWarningMenu(
                            expanded = true,
                            onDismissRequest = { cancelWarningRequest = null },
                            onConfirm = {
                                val activeRequest = cancelWarningRequest
                                cancelWarningRequest = null
                                val ids = activeRequest?.ids.orEmpty()
                                if (activeRequest?.isCancelAll == true) {
                                    DownloadService.cancelAll()
                                } else {
                                    ids.forEach(DownloadService::cancelDownload)
                                }
                                onSelectDownload(null)
                            },
                            isCancelAll = request.isCancelAll,
                        )
                    }
                }

                // Clear button - clears completed, cancelled, and failed downloads
                val hasCompletedOrCancelled =
                    downloads.any {
                        val s = it.second.getStatusFlow().value
                        s == DownloadPhase.COMPLETE || s == DownloadPhase.CANCELLED || s == DownloadPhase.FAILED
                    }

                DownloadsQueueButton(
                    label = stringResource(R.string.downloads_queue_clear),
                    accentColor = TextSecondary,
                    onClick = {
                        DownloadService.clearCompletedDownloads()
                    },
                    enabled = hasCompletedOrCancelled,
                )
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

            // Sort so the user always sees what's actually running first, then everything
            // they can resume, then finished items, with cancelled at the very bottom.
            // The list re-sorts on phase transitions because `tick` (incremented by the
            // status flow collectors above) is read here, forcing recomposition.
            @Suppress("UNUSED_EXPRESSION")
            tick
            val sortedDownloads =
                downloads.sortedBy { (_, info) ->
                    when (info.getStatusFlow().value) {
                        // In-progress states grouped together at the top.
                        DownloadPhase.DOWNLOADING,
                        DownloadPhase.PREPARING,
                        DownloadPhase.VERIFYING,
                        DownloadPhase.PATCHING,
                        DownloadPhase.APPLYING_DATA,
                        DownloadPhase.FINALIZING,
                        DownloadPhase.UNPACKING,
                        DownloadPhase.UNKNOWN,
                        -> 0
                        // FAILED sorts with PAUSED — both are user-resumable;
                        // don't bury them under finished downloads.
                        DownloadPhase.PAUSED -> 1
                        DownloadPhase.FAILED -> 1
                        DownloadPhase.QUEUED -> 2
                        DownloadPhase.COMPLETE -> 3
                        DownloadPhase.CANCELLED -> 5
                    }
                }

            if (sortedDownloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyStateMessage(stringResource(R.string.downloads_queue_empty))
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sortedDownloads, key = { it.first }) { (id, info) ->
                        DownloadItemDeck(
                            id,
                            info,
                            isSelected = selectedId == id,
                            animationsActive = animationsActive,
                            onClick = {
                                if (selectedId == id) onSelectDownload(null) else onSelectDownload(id)
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DownloadsQueueButton(
        label: String,
        accentColor: Color,
        enabled: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        val contentColor = if (enabled) accentColor else TextSecondary.copy(alpha = 0.48f)

        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(40.dp).widthIn(min = 96.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = DownloadButtonBlack,
                    contentColor = contentColor,
                    disabledContainerColor = DownloadButtonBlack.copy(alpha = 0.18f),
                    disabledContentColor = TextSecondary.copy(alpha = 0.48f),
                ),
            border = BorderStroke(1.dp, contentColor.copy(alpha = if (enabled) 0.55f else 0.24f)),
            contentPadding = PaddingValues(horizontal = 8.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun AnimatedDownloadProgressFill(
        modifier: Modifier,
        widthPx: Float,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "downloadProgressGradient")
        val gradientOffset by infiniteTransition.animateFloat(
            initialValue = -widthPx,
            targetValue = 0f,
                animationSpec =
                    infiniteRepeatable(
                    animation = tween(durationMillis = 5000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "downloadProgressGradientOffset",
        )

        Box(
            modifier.background(
                Brush.horizontalGradient(
                    colorStops = DownloadChaseGradientStops,
                    startX = gradientOffset,
                    endX = gradientOffset + (widthPx * 2f),
                    tileMode = TileMode.Repeated,
                ),
            ),
        )
    }

    @Composable
    private fun DownloadChasingProgressBar(
        progress: Float,
        status: DownloadPhase,
        animationsActive: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val shouldUseActiveGradient =
            when (status) {
                DownloadPhase.DOWNLOADING,
                DownloadPhase.QUEUED,
                DownloadPhase.PREPARING,
                DownloadPhase.VERIFYING,
                DownloadPhase.PATCHING,
                DownloadPhase.APPLYING_DATA,
                DownloadPhase.FINALIZING,
                DownloadPhase.UNPACKING,
                -> true
                else -> false
            }
        val shouldAnimate = shouldUseActiveGradient && animationsActive
        val fillColor =
            when (status) {
                DownloadPhase.FAILED,
                DownloadPhase.CANCELLED,
                -> DangerRed
                DownloadPhase.COMPLETE -> StatusOnline
                DownloadPhase.PAUSED -> TextSecondary
                else -> Accent
            }

        BoxWithConstraints(
            modifier =
                modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.34f)),
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }

            if (clampedProgress > 0f) {
                val fillModifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(clampedProgress)
                        .clip(RectangleShape)

                if (shouldUseActiveGradient) {
                    if (shouldAnimate) {
                        AnimatedDownloadProgressFill(fillModifier, widthPx)
                    } else {
                        Box(
                            fillModifier.background(
                                Brush.horizontalGradient(
                                    colorStops = DownloadChaseGradientStops,
                                    endX = widthPx * 2f,
                                    tileMode = TileMode.Repeated,
                                ),
                            ),
                        )
                    }
                } else {
                    Box(fillModifier.background(fillColor))
                }
            }
        }
    }

    /**
     * The live progress body (phase label, bar, percentage, byte counts) for a
     * game's in-flight download / verify. Observes [info] directly so it
     * refreshes live. Rendered inside [SteamTaskProgressDialog].
     */
    @Composable
    private fun SteamTaskProgressBody(info: DownloadInfo) {
        var progress by remember(info) { mutableFloatStateOf(info.getProgress()) }
        DisposableEffect(info) {
            val listener: (Float) -> Unit = { progress = it }
            info.addProgressListener(listener)
            onDispose { info.removeProgressListener(listener) }
        }
        val status by info.getStatusFlow().collectAsState()
        // The status message carries a unique suffix every progress tick;
        // keying the byte sample on it (and on `progress`) keeps the card
        // refreshing live — the Downloads-tab row relies on the same.
        val statusMessage by info.getStatusMessageFlow().collectAsState()
        val fraction = progress.coerceIn(0f, 1f)
        val animatedFraction by animateFloatAsState(
            targetValue = fraction,
            animationSpec = tween(durationMillis = 400),
            label = "steamTaskProgress",
        )
        val (doneBytes, totalBytes) =
            remember(progress, statusMessage) { info.getDisplayBytesProgress() }

        val phaseText =
            when (status) {
                DownloadPhase.VERIFYING -> "Verifying…"
                DownloadPhase.DOWNLOADING -> "Downloading…"
                DownloadPhase.PAUSED -> "Paused"
                DownloadPhase.QUEUED -> "Queued"
                DownloadPhase.PREPARING -> "Preparing…"
                DownloadPhase.PATCHING -> "Patching…"
                DownloadPhase.APPLYING_DATA -> "Installing…"
                DownloadPhase.UNPACKING -> "Unpacking…"
                DownloadPhase.FINALIZING -> "Finalizing…"
                DownloadPhase.COMPLETE -> "Complete"
                DownloadPhase.FAILED -> "Failed"
                DownloadPhase.CANCELLED -> "Cancelled"
                else -> "Working…"
            }
        val phaseColor =
            when (status) {
                DownloadPhase.COMPLETE -> StatusOnline
                DownloadPhase.FAILED, DownloadPhase.CANCELLED -> DangerRed
                DownloadPhase.PAUSED, DownloadPhase.QUEUED -> StatusAway
                else -> Accent
            }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(phaseText, color = phaseColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${(fraction * 100).toInt()}%",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            DownloadChasingProgressBar(
                progress = animatedFraction,
                status = status,
                animationsActive = true,
                modifier = Modifier.fillMaxWidth().height(10.dp),
            )
            Text(
                if (totalBytes > 0L) {
                    "${StorageUtils.formatDecimalSize(doneBytes)} / " +
                        StorageUtils.formatDecimalSize(totalBytes)
                } else {
                    " "
                },
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
    }

    /**
     * Activity-root host for the Verify Files progress pop-up + completion
     * notice. Rendered once near the NavHost so it outlives the game-detail
     * dialogs that start the task — the verify-completion library refresh
     * tears those down, and a dialog-scoped watcher would miss COMPLETE.
     *
     * Reads the `taskProgress*` activity fields; [showTaskProgressPopup]
     * populates them. The watcher is keyed on [taskProgressInfo] so it
     * re-attaches if recomposed and (because the status is a StateFlow)
     * still observes a terminal phase that landed in between.
     */
    @Composable
    private fun TaskProgressHost() {
        val info = taskProgressInfo
        LaunchedEffect(info) {
            if (info == null) return@LaunchedEffect
            info.getStatusFlow().collect { st ->
                when (st) {
                    DownloadPhase.COMPLETE -> {
                        // Snapshot first: `taskProgressShown = false` can trigger
                        // a follow-up task that overwrites these fields before we read.
                        val msg = taskProgressCompleteMsg
                        val asToast = taskProgressCompleteAsToast
                        taskProgressShown = false
                        taskProgressInfo = null
                        if (asToast) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                this@UnifiedActivity,
                                msg,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                        } else {
                            taskDoneFailed = false
                            taskDoneMessage = msg
                        }
                    }
                    DownloadPhase.FAILED -> {
                        taskProgressShown = false
                        taskDoneFailed = true
                        taskDoneMessage = taskProgressFailedMsg
                        taskProgressInfo = null
                    }
                    DownloadPhase.CANCELLED -> {
                        taskProgressShown = false
                        taskProgressInfo = null
                    }
                    else -> Unit
                }
            }
        }
        if (taskCheckingShown) {
            TaskCheckingDialog(
                gameName = taskCheckingGameName,
                onDismissRequest = { taskCheckingShown = false },
            )
        }
        if (info != null && taskProgressShown) {
            SteamTaskProgressDialog(
                info = info,
                gameName = taskProgressGameName,
                onDismissRequest = { taskProgressShown = false },
            )
        }
        taskDoneMessage?.let { msg ->
            TaskCompleteDialog(
                message = msg,
                failed = taskDoneFailed,
                onClose = { taskDoneMessage = null },
            )
        }
    }

    /**
     * Indeterminate "Checking for updates…" pop-up — same frame as
     * [SteamTaskProgressDialog] but without a known task to track. Dismissable;
     * the underlying check keeps running and the host shows the result.
     */
    @Composable
    private fun TaskCheckingDialog(
        gameName: String,
        onDismissRequest: () -> Unit,
    ) {
        Dialog(onDismissRequest = onDismissRequest) {
            PopupDialog(
                title = gameName,
                message = stringResource(R.string.store_game_checking_updates),
                icon = Icons.Outlined.Sync,
                accentColor = Accent,
                modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
                content = {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Accent,
                    )
                },
                footer = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        PopupTextAction(
                            label = stringResource(R.string.common_ui_close),
                            textColor = Accent,
                            onClick = onDismissRequest,
                        )
                    }
                },
            )
        }
    }

    /**
     * Dismissable pop-up showing live progress for a Steam task (verify /
     * update). Tapping outside closes it — the task keeps running and stays
     * visible in the Downloads tab. The host watches the task to completion
     * separately and shows [TaskCompleteDialog] when it finishes.
     */
    @Composable
    private fun SteamTaskProgressDialog(
        info: DownloadInfo,
        gameName: String,
        onDismissRequest: () -> Unit,
    ) {
        Dialog(onDismissRequest = onDismissRequest) {
            PopupDialog(
                title = gameName,
                icon = Icons.Outlined.Download,
                accentColor = Accent,
                modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
                content = {
                    SteamTaskProgressBody(info)
                    Text(
                        stringResource(R.string.store_game_progress_background_hint),
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                },
                footer = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        PopupTextAction(
                            label = stringResource(R.string.common_ui_close),
                            textColor = Accent,
                            onClick = onDismissRequest,
                        )
                    }
                },
            )
        }
    }

    /**
     * Small completion notice ("Verify Files Complete" / "… Failed") with a
     * single Close button. Shown by the host once a watched task finishes.
     */
    @Composable
    private fun TaskCompleteDialog(message: String, failed: Boolean, onClose: () -> Unit) {
        Dialog(onDismissRequest = onClose) {
            PopupDialog(
                title = message,
                icon = if (failed) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
                accentColor = if (failed) DangerRed else StatusOnline,
                confirmButtonColor = Accent,
                confirmLabel = stringResource(R.string.common_ui_close),
                onConfirm = onClose,
                modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
            )
        }
    }

    @Composable
    private fun DownloadCancelWarningMenu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        onConfirm: () -> Unit,
        isCancelAll: Boolean = false,
    ) {
        val titleRes = if (isCancelAll) R.string.downloads_queue_cancel_all_title else R.string.downloads_queue_cancel_download_title
        val messageRes = if (isCancelAll) R.string.downloads_queue_cancel_all_warning else R.string.downloads_queue_cancel_download_warning
        val confirmRes = if (isCancelAll) R.string.downloads_queue_cancel_all else R.string.downloads_queue_cancel_download
        LaunchDangerConfirmDialog(
            visible = expanded,
            title = stringResource(titleRes),
            message = stringResource(messageRes),
            confirmLabel = stringResource(confirmRes),
            onDismissRequest = onDismissRequest,
            onConfirm = onConfirm,
            icon = Icons.Outlined.Warning,
            titleTextAlign = TextAlign.Center,
            messageTextAlign = TextAlign.Center,
        )
    }

    @Composable
    fun DownloadItemDeck(
        id: String,
        info: DownloadInfo,
        isSelected: Boolean,
        animationsActive: Boolean,
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
        var previousStatus by remember { mutableStateOf(status) }
        var showCompletedProgressBar by remember { mutableStateOf(status != DownloadPhase.COMPLETE) }
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
        val clickInteractionSource = remember { MutableInteractionSource() }
        val animatedProgress by animateFloatAsState(
            targetValue = if (status == DownloadPhase.COMPLETE) 1f else progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            label = "downloadItemProgress",
        )

        LaunchedEffect(status) {
            if (status == DownloadPhase.COMPLETE) {
                if (previousStatus != DownloadPhase.COMPLETE) {
                    showCompletedProgressBar = true
                    delay(900)
                }
                showCompletedProgressBar = false
            } else {
                showCompletedProgressBar = true
            }
            previousStatus = status
        }

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
            color = if (isSelected) DownloadCardSelectedBlack else DownloadCardBlack,
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .chasingBorder(
                        isFocused = isFocused || isSelected,
                        paused = chasingBordersPaused.value || !animationsActive,
                        cornerRadius = 12.dp,
                        borderWidth = 2.dp,
                        animationDurationMs = 8000,
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable(
                        interactionSource = clickInteractionSource,
                        indication = null,
                        onClick = onClick,
                    ),
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
                    val (downloadedBytes, totalBytes) = info.getDisplayBytesProgress()
                    val speed = info.getCurrentDownloadSpeed() ?: 0L
                    val percentage = (animatedProgress * 100).roundToInt()
                    val showDownloadSpeed =
                        status == DownloadPhase.DOWNLOADING &&
                            progress < 1f &&
                            speed > 0

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
                            text = "${StorageUtils.formatDecimalSize(downloadedBytes)} / ${StorageUtils.formatDecimalSize(totalBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            if (showDownloadSpeed) {
                                Text(
                                    text = StorageUtils.formatBitsPerSecond(speed),
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
                                currentFile?.let {
                                    stringResource(R.string.downloads_queue_phase_downloading_file, it.take(10))
                                } ?: stringResource(R.string.downloads_queue_phase_downloading)
                            }

                            DownloadPhase.PAUSED -> {
                                stringResource(R.string.downloads_queue_phase_paused)
                            }

                            DownloadPhase.QUEUED -> {
                                stringResource(R.string.downloads_queue_phase_queued)
                            }

                            DownloadPhase.PREPARING -> {
                                stringResource(R.string.downloads_queue_phase_preparing)
                            }

                            DownloadPhase.VERIFYING -> {
                                currentFile?.let {
                                    stringResource(R.string.downloads_queue_phase_verifying_file, it.take(10))
                                } ?: stringResource(R.string.downloads_queue_phase_verifying)
                            }

                            DownloadPhase.PATCHING -> {
                                stringResource(R.string.downloads_queue_phase_patching)
                            }

                            DownloadPhase.APPLYING_DATA -> {
                                stringResource(R.string.downloads_queue_phase_applying_data)
                            }

                            DownloadPhase.FINALIZING -> {
                                stringResource(R.string.downloads_queue_phase_finalizing)
                            }

                            DownloadPhase.UNPACKING -> {
                                stringResource(R.string.downloads_queue_phase_unpacking)
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
                                stringResource(R.string.downloads_queue_phase_unknown)
                            }
                        }
                    val statusColor =
                        when (status) {
                            DownloadPhase.COMPLETE -> StatusOnline
                            DownloadPhase.FAILED,
                            DownloadPhase.CANCELLED,
                            -> DangerRed
                            DownloadPhase.PAUSED,
                            DownloadPhase.QUEUED,
                            -> StatusAway
                            DownloadPhase.DOWNLOADING,
                            DownloadPhase.PREPARING,
                            DownloadPhase.VERIFYING,
                            DownloadPhase.PATCHING,
                            DownloadPhase.APPLYING_DATA,
                            DownloadPhase.FINALIZING,
                            DownloadPhase.UNPACKING,
                            -> Accent
                            else -> TextSecondary
                        }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.downloads_queue_status_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    AnimatedVisibility(
                        visible = status != DownloadPhase.COMPLETE || showCompletedProgressBar,
                        exit = fadeOut(tween(180)) + shrinkVertically(tween(180)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            DownloadChasingProgressBar(
                                progress = if (status == DownloadPhase.COMPLETE) 1f else animatedProgress,
                                status = status,
                                animationsActive = animationsActive,
                                modifier = Modifier.weight(1f).height(9.dp).padding(end = 10.dp),
                            )
                            Text(
                                text = "$percentage%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (status == DownloadPhase.COMPLETE) StatusOnline else TextPrimary,
                                modifier = Modifier.width(40.dp),
                            )
                        }
                    }
                }

                Box(contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = status != DownloadPhase.COMPLETE && status != DownloadPhase.CANCELLED,
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.downloads_queue_cancel_download),
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
                    if (showDeleteDialog) {
                        DownloadCancelWarningMenu(
                            expanded = true,
                            onDismissRequest = { showDeleteDialog = false },
                            onConfirm = {
                                showDeleteDialog = false
                                DownloadService.cancelDownload(id)
                            },
                        )
                    }
                }
                if (ControllerHelper.isControllerConnected()) {
                    Spacer(Modifier.width(8.dp))
                    ControllerBadge(if (ControllerHelper.isPlayStationController()) "\u2715" else "A")
                }
            }
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
        var selectedManifestSizes by remember { mutableStateOf(SteamService.ManifestSizes()) }
        var dlcApps by remember { mutableStateOf<List<SteamApp>>(emptyList()) }
        var dlcSizes by remember { mutableStateOf<Map<Int, SteamService.ManifestSizes>>(emptyMap()) }
        var installedDlcIds by remember(app.id) { mutableStateOf<Set<Int>>(emptySet()) }
        var installed by remember(app.id) { mutableStateOf<Boolean?>(null) }
        val selectedDlcIds = remember { mutableStateListOf<Int>() }
        var customPath by remember { mutableStateOf<String?>(null) }
        var showCustomPathWarning by remember { mutableStateOf(false) }
        var isCheckingForUpdate by remember(app.id) { mutableStateOf(false) }
        var isUpdateCheckCoolingDown by remember(app.id) { mutableStateOf(false) }
        var showWorkshopDialog by remember(app.id) { mutableStateOf(false) }
        var updateInfo by remember(app.id) { mutableStateOf<SteamService.SteamUpdateInfo?>(null) }
        var updateStatusText by remember(app.id) { mutableStateOf<String?>(null) }
        val downloadRecords by com.winlator.cmod.app.service.download.DownloadCoordinator.records.collectAsState(
            initial = com.winlator.cmod.app.service.download.DownloadCoordinator.snapshotRecords(),
        )
        val scope = rememberCoroutineScope()

        if (showCustomPathWarning) {
            CustomPathWarningDialog(
                onDismiss = { showCustomPathWarning = false },
                onProceed = {
                    showCustomPathWarning = false
                    DirectoryPickerDialog.show(
                        activity = this@UnifiedActivity,
                        initialPath = customPath ?: SteamService.defaultAppInstallPath,
                        title = getString(R.string.settings_content_install_directory),
                    ) { path -> customPath = path }
                },
            )
        }

        data class SteamInstallLoadData(
            val dlcApps: List<SteamApp>,
            val dlcSizes: Map<Int, SteamService.ManifestSizes>,
            val installedDlcIds: Set<Int>,
            val baseManifestSizes: SteamService.ManifestSizes,
            val installed: Boolean,
        )

        LaunchedEffect(app.id, downloadRecords) {
            val loadData =
                withContext(Dispatchers.IO) {
                    val selectableDlcApps = SteamService.getSelectableDlcAppsOf(app.id)
                    val perDlcSizes =
                        selectableDlcApps.associate { dlc ->
                            dlc.id to SteamService.getDlcOnlyManifestSizes(app.id, dlc.id)
                        }
                    val installedDlcIds =
                        SteamService.getInstalledDlcDepotsOf(app.id)
                            .orEmpty()
                            .toSet()
                    SteamInstallLoadData(
                        dlcApps = selectableDlcApps,
                        dlcSizes = perDlcSizes,
                        installedDlcIds = installedDlcIds,
                        baseManifestSizes = SteamService.getInstallableSelectedManifestSizes(app.id),
                        installed = SteamService.isAppInstalled(app.id),
                    )
                }
            dlcApps = loadData.dlcApps
            dlcSizes = loadData.dlcSizes
            installedDlcIds = loadData.installedDlcIds
            selectedDlcIds.removeAll(loadData.installedDlcIds)
            selectedManifestSizes = loadData.baseManifestSizes
            installed = loadData.installed
            isLoading = false
        }

        LaunchedEffect(app.id, selectedDlcIds.toList()) {
            selectedManifestSizes =
                withContext(Dispatchers.IO) {
                    SteamService.getInstallableSelectedManifestSizes(app.id, selectedDlcIds.toList())
                }
        }

        val totalDownloadSize = selectedManifestSizes.downloadSize
        val totalInstallSize = selectedManifestSizes.installSize
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
        val isInstallEnabled = totalInstallSize == 0L || availableBytes >= totalInstallSize
        val installPathDisplay = customPath ?: SteamService.defaultAppInstallPath

        val dlcItems =
            remember(dlcApps, dlcSizes, installedDlcIds) {
                dlcApps.map { dlc ->
                    val sizes = dlcSizes[dlc.id]
                    val size =
                        sizes
                            ?.downloadSize
                            ?.takeIf { it > 0L }
                            ?: sizes?.installSize
                            ?: 0L
                    StoreDlcItem(
                        id = dlc.id,
                        name = dlc.name,
                        downloadSize = size,
                        isInstalled = dlc.id in installedDlcIds,
                    )
                }
            }
        val customPathLabel =
            when {
                customPath != null -> stringResource(R.string.common_ui_custom)
                defaultPathSet -> stringResource(R.string.common_ui_already_set)
                else -> stringResource(R.string.common_ui_custom)
            }
        val isReallyInstalled = installed == true
        val steamDownloadRecord =
            downloadRecords.firstOrNull {
                it.store == com.winlator.cmod.app.db.download.DownloadRecord.STORE_STEAM &&
                    it.storeGameId == app.id.toString() &&
                    it.status in setOf(
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_QUEUED,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_DOWNLOADING,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_PAUSED,
                    )
        }
        val hasBlockingSteamDownload =
            downloadRecords.any {
                it.store == com.winlator.cmod.app.db.download.DownloadRecord.STORE_STEAM &&
                    it.storeGameId == app.id.toString() &&
                    it.status in setOf(
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_QUEUED,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_DOWNLOADING,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_PAUSED,
                        com.winlator.cmod.app.db.download.DownloadRecord.STATUS_FAILED,
                    )
            }
        val updateActionEnabled = steamDownloadRecord == null
        val installActionEnabled = isInstallEnabled && steamDownloadRecord == null
        val activeSteamDownloadText = stringResource(R.string.store_game_download_already_active)
        val noUpdateAvailableText = stringResource(R.string.store_game_no_update_available)
        val updateAvailableText = stringResource(R.string.store_game_update_available)
        val updateFailedText = stringResource(R.string.store_game_update_check_failed)

        Dialog(
            onDismissRequest = onDismissRequest,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                color = Color.Black,
            ) {
                StoreGameDetailScreen(
                    title = app.name,
                    subtitle =
                        listOfNotNull(
                            app.developer.takeIf { it.isNotBlank() },
                            app.publisher.takeIf {
                                it.isNotBlank() && !it.equals(app.developer, ignoreCase = true)
                            },
                        ).joinToString(" • "),
                    sourceLabel = "Steam",
                    heroImageUrl = StoreArtworkCache.imageModel(context, StoreArtworkCache.steamRef(app, "hero", app.getHeroUrl())),
                    isLoading = isLoading,
                    isInstalled = isReallyInstalled,
                    installPathDisplay = installPathDisplay,
                    downloadSize = totalDownloadSize,
                    installSize = totalInstallSize,
                    availableBytes = availableBytes,
                    isInstallEnabled = isInstallEnabled,
                    isDownloadActionEnabled = installActionEnabled,
                    customPathLabel = customPathLabel,
                    showCustomPath = true,
                    showCloudSync = false,
                    showUninstall = false,
                    showUpdateCheck = true,
                    isCheckingForUpdate = isCheckingForUpdate,
                    isUpdateAvailable = updateInfo?.hasUpdate == true,
                    updateDownloadSize = updateInfo?.downloadSize ?: 0L,
                    updateStatusText = updateStatusText,
                    isUpdateActionEnabled = updateActionEnabled,
                    isUpdateCheckCoolingDown = isUpdateCheckCoolingDown,
                    // Shown for any installed game; titles without UGC simply
                    // open to an empty Workshop window (handled gracefully).
                    showWorkshop = isReallyInstalled,
                    showVerifyFiles = isReallyInstalled,
                    areSteamActionsEnabled = !hasBlockingSteamDownload,
                    dlcs = dlcItems,
                    selectedDlcIds = selectedDlcIds.toSet(),
                    isDlcSelectionEnabled = steamDownloadRecord == null,
                    onBack = onDismissRequest,
                    onInstall = {
                        if (steamDownloadRecord != null) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                activeSteamDownloadText,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            return@StoreGameDetailScreen
                        }
                        context.runIfOnlineOrToast {
                            scope.launch(Dispatchers.IO) {
                                val installableDlcIds = dlcItems
                                    .filter { !it.isInstalled && it.id in selectedDlcIds }
                                    .map { it.id }
                                SteamService.downloadApp(app.id, installableDlcIds, false, customPath)
                                withContext(Dispatchers.Main) { onDismissRequest() }
                            }
                        }
                    },
                    onCheckForUpdate = { startUpdateCheck(app.id, app.name) },
                    onWorkshop = { showWorkshopDialog = true },
                    onVerifyFiles = {
                        if (steamDownloadRecord != null) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                context,
                                activeSteamDownloadText,
                                android.widget.Toast.LENGTH_SHORT,
                            )
                            return@StoreGameDetailScreen
                        }
                        context.runIfOnlineOrToast {
                            scope.launch {
                                val started =
                                    withContext(Dispatchers.IO) {
                                        SteamService.downloadAppForVerify(app.id)
                                    }
                                if (started != null) {
                                    // Hand off to the activity-root host so the
                                    // pop-up + completion notice outlive this dialog.
                                    showTaskProgressPopup(
                                        started,
                                        app.name,
                                        getString(R.string.store_game_verify_complete),
                                        getString(R.string.store_game_verify_failed_notice),
                                        completeAsToast = true,
                                    )
                                }
                            }
                        }
                    },
                    onDownloadUpdate = {
                        if (!updateActionEnabled || updateInfo?.hasUpdate != true) return@StoreGameDetailScreen
                        context.runIfOnlineOrToast {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val latest = SteamService.checkForAppUpdate(app.id)
                                    withContext(Dispatchers.Main) {
                                        updateInfo = latest
                                        updateStatusText =
                                            when {
                                                latest.hasUpdate -> updateAvailableText
                                                latest.message != null -> updateFailedText
                                                else -> null
                                            }
                                    }
                                    if (!latest.hasUpdate) {
                                        withContext(Dispatchers.Main) {
                                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                                context,
                                                noUpdateAvailableText,
                                                android.widget.Toast.LENGTH_SHORT,
                                            )
                                        }
                                        return@launch
                                    }

                                    SteamService.downloadAppForUpdate(app.id, latest.depotIds)
                                    withContext(Dispatchers.Main) { onDismissRequest() }
                                } catch (e: Exception) {
                                    Log.w("UnifiedActivity", "Steam update download failed to start for appId=${app.id}", e)
                                    withContext(Dispatchers.Main) {
                                        updateStatusText = updateFailedText
                                    }
                                }
                            }
                        }
                    },
                    onCustomPath = {
                        if (customPath == null && defaultPathSet) {
                            showCustomPathWarning = true
                        } else {
                            DirectoryPickerDialog.show(
                                activity = this@UnifiedActivity,
                                initialPath = customPath ?: SteamService.defaultAppInstallPath,
                                title = getString(R.string.settings_content_install_directory),
                            ) { path -> customPath = path }
                        }
                    },
                    onToggleDlc = { id ->
                        if (steamDownloadRecord != null) {
                            return@StoreGameDetailScreen
                        }
                        if (dlcItems.any { it.id == id && it.isInstalled }) {
                            return@StoreGameDetailScreen
                        }
                        if (selectedDlcIds.contains(id)) {
                            selectedDlcIds.remove(id)
                        } else {
                            selectedDlcIds.add(id)
                        }
                    },
                    onToggleSelectAllDlcs = {
                        if (steamDownloadRecord != null) {
                            return@StoreGameDetailScreen
                        }
                        val selectableDlcItems = dlcItems.filterNot { it.isInstalled }
                        val all = selectableDlcItems.isNotEmpty() && selectableDlcItems.all { it.id in selectedDlcIds }
                        if (all) {
                            selectedDlcIds.removeAll(selectableDlcItems.map { it.id }.toSet())
                        } else {
                            selectableDlcItems.forEach { if (it.id !in selectedDlcIds) selectedDlcIds.add(it.id) }
                        }
                    },
                )
            }
        }

        if (showWorkshopDialog) {
            WorkshopDialog(
                appId = app.id,
                gameTitle = app.name,
                onDismissRequest = { showWorkshopDialog = false },
            )
        }
    }

    @Composable
    private fun WorkshopDialog(
        appId: Int,
        gameTitle: String,
        onDismissRequest: () -> Unit,
    ) {
        var loadState by remember(appId) { mutableStateOf(WorkshopLoadState.LOADING) }
        var errorMessage by remember(appId) { mutableStateOf<String?>(null) }
        var allItems by remember(appId) { mutableStateOf<List<StoreWorkshopItem>>(emptyList()) }
        var query by remember(appId) { mutableStateOf("") }
        // Published-file-ids with an install OR uninstall in flight.
        val busyIds = remember(appId) { mutableStateListOf<Long>() }
        var reloadKey by remember(appId) { mutableStateOf(0) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(appId, reloadKey) {
            loadState = WorkshopLoadState.LOADING
            errorMessage = null
            // Drop any in-flight spinners — a reload re-fetches the list.
            busyIds.clear()
            try {
                val items =
                    withContext(Dispatchers.IO) {
                        val json = SteamService.getSubscribedWorkshopItems(appId)
                        if (json == null) {
                            null
                        } else {
                            val installed =
                                com.winlator.cmod.feature.stores.steam.workshop.WorkshopModsGenerator
                                    .installedItemIds(applicationContext, appId)
                            parseWorkshopItemsJson(json, installed)
                        }
                    }
                if (items == null) {
                    errorMessage =
                        "Couldn't load your Workshop subscriptions. " +
                            "Make sure you're signed in to Steam and online."
                    loadState = WorkshopLoadState.ERROR
                } else {
                    allItems = items
                    loadState = WorkshopLoadState.READY
                }
            } catch (e: Exception) {
                Log.w("UnifiedActivity", "Workshop load failed for appId=$appId", e)
                errorMessage = e.message
                loadState = WorkshopLoadState.ERROR
            }
        }

        val filtered =
            remember(allItems, query) {
                val q = query.trim()
                if (q.isBlank()) {
                    allItems
                } else {
                    allItems.filter {
                        it.title.contains(q, ignoreCase = true) ||
                            it.author.contains(q, ignoreCase = true)
                    }
                }
            }

        Dialog(
            onDismissRequest = onDismissRequest,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            StoreWorkshopScreen(
                gameTitle = gameTitle,
                loadState = loadState,
                errorMessage = errorMessage,
                items = filtered,
                query = query,
                // Snapshotted here inside the Dialog content lambda: a mutation of
                // the SnapshotStateList invalidates this scope, so .toSet() re-runs.
                busyIds = busyIds.toSet(),
                onQueryChange = { query = it },
                onInstall = { id ->
                    val item = allItems.firstOrNull { it.publishedFileId == id }
                    if (item != null && id !in busyIds) {
                        if (item.manifestId == 0L) {
                            com.winlator.cmod.shared.ui.toast.WinToast.show(
                                this@UnifiedActivity,
                                "This Workshop item has no downloadable content",
                                android.widget.Toast.LENGTH_SHORT,
                            )
                        } else {
                            busyIds.add(id)
                            scope.launch {
                                val ok =
                                    SteamService.installWorkshopItem(
                                        appId = appId,
                                        publishedFileId = item.publishedFileId,
                                        manifestId = item.manifestId,
                                        title = item.title,
                                        fileSizeBytes = item.fileSizeBytes,
                                        timeUpdated = item.timeUpdated,
                                        previewUrl = item.previewImageUrl ?: "",
                                    )
                                if (ok) {
                                    allItems =
                                        allItems.map {
                                            if (it.publishedFileId == id) it.copy(isInstalled = true) else it
                                        }
                                } else {
                                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                                        this@UnifiedActivity,
                                        "Workshop download failed — check your Steam connection",
                                        android.widget.Toast.LENGTH_LONG,
                                    )
                                }
                                busyIds.remove(id)
                            }
                        }
                    }
                },
                onUninstall = { id ->
                    if (id !in busyIds) {
                        busyIds.add(id)
                        scope.launch {
                            val ok =
                                withContext(Dispatchers.IO) {
                                    com.winlator.cmod.feature.stores.steam.workshop.WorkshopModsGenerator
                                        .uninstall(applicationContext, appId, id)
                                }
                            if (ok) {
                                allItems =
                                    allItems.map {
                                        if (it.publishedFileId == id) it.copy(isInstalled = false) else it
                                    }
                            } else {
                                com.winlator.cmod.shared.ui.toast.WinToast.show(
                                    this@UnifiedActivity,
                                    "Couldn't uninstall this Workshop item",
                                    android.widget.Toast.LENGTH_SHORT,
                                )
                            }
                            busyIds.remove(id)
                        }
                    }
                },
                onRetry = { reloadKey++ },
                onClose = onDismissRequest,
            )
        }
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

    private fun findLibraryArtworkShortcut(
        shortcuts: List<Shortcut>,
        app: SteamApp,
        gogGame: GOGGame?,
        epicGame: EpicGame?,
    ): Shortcut? =
        when {
            gogGame != null -> {
                shortcuts.find {
                    it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == gogGame.id
                }
            }

            epicGame != null -> {
                shortcuts.find {
                    it.getExtra("game_source") == "EPIC" && it.getExtra("app_id") == epicGame.id.toString()
                }
            }

            else -> {
                findShortcutForGame(
                    shortcuts = shortcuts,
                    app = app,
                    isCustom = app.id < 0,
                    isEpic = app.id >= 2000000000,
                    epicId = if (app.id >= 2000000000) app.id - 2000000000 else 0,
                )
            }
        }

    private fun Shortcut.hasExistingArtwork(extraKey: String): Boolean =
        (getExtra(extraKey)
            .takeIf { it.isNotBlank() }
            ?.let { java.io.File(it).isFile } == true)

    private fun artworkCacheId(
        app: SteamApp,
        gogGame: GOGGame?,
        epicGame: EpicGame?,
    ): ArtworkCacheId? =
        when {
            gogGame != null -> ArtworkCacheId("gog", gogGame.id)
            epicGame != null -> ArtworkCacheId("epic", epicGame.id.toString())
            app.id >= 0 -> ArtworkCacheId("steam", app.id.toString())
            else -> null
        }

    private fun customArtworkOverrideSlots(
        app: SteamApp,
        gogGame: GOGGame?,
        epicGame: EpicGame?,
        hasDefaultCustomArt: Boolean,
        hasGridCustomArt: Boolean,
        hasCarouselCustomArt: Boolean,
        hasListCustomArt: Boolean,
        hasHeroCustomArt: Boolean,
    ): Set<String> {
        val overridesPrimary = hasDefaultCustomArt || hasGridCustomArt || hasCarouselCustomArt || hasListCustomArt
        if (!overridesPrimary && !hasHeroCustomArt) return emptySet()

        return when {
            gogGame != null -> {
                buildSet {
                    if (overridesPrimary) {
                        add("cover")
                        add("icon")
                    }
                    if (hasHeroCustomArt) add("hero")
                }
            }

            epicGame != null -> {
                buildSet {
                    if (overridesPrimary) {
                        add("cover")
                        add("square")
                        add("logo")
                    }
                    if (hasHeroCustomArt) add("hero")
                }
            }

            app.id >= 0 -> {
                buildSet {
                    if (hasDefaultCustomArt || hasGridCustomArt) add("capsule")
                    if (hasDefaultCustomArt || hasCarouselCustomArt) add("library_capsule")
                    if (hasDefaultCustomArt || hasListCustomArt) add("small_capsule")
                    if (hasHeroCustomArt) add("hero")
                }
            }

            else -> emptySet()
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

    private fun isShortcutOfflineMode(shortcut: Shortcut?): Boolean =
        shortcut != null && shortcut.getExtra("offline_mode", "0") == "1"

    private fun setShortcutOfflineMode(
        shortcut: Shortcut?,
        enabled: Boolean,
    ) {
        if (shortcut == null) return
        shortcut.putExtra("offline_mode", if (enabled) "1" else null)
        shortcut.saveData()
    }

    private fun repairShortcutDisplayNameIfNeeded(
        shortcut: Shortcut,
        displayName: String,
        vararg technicalNames: String,
    ) {
        if (displayName.isBlank() || !shortcut.file.isFile) return

        runCatching {
            val technicalNameSet = (technicalNames.toList() + shortcut.file.nameWithoutExtension)
                .filter { it.isNotBlank() }
                .toSet()
            val lines = com.winlator.cmod.shared.io.FileUtils.readLines(shortcut.file)
            val sb = StringBuilder()
            var changed = false
            var sawName = false

            for (line in lines) {
                if (line.startsWith("Name=")) {
                    sawName = true
                    val currentName = line.removePrefix("Name=").trim()
                    if (currentName.isBlank() || currentName in technicalNameSet) {
                        sb.append("Name=").append(displayName).append('\n')
                        changed = true
                    } else {
                        sb.append(line).append('\n')
                    }
                } else {
                    sb.append(line).append('\n')
                }
            }

            if (!sawName) {
                val desktopHeader = "[Desktop Entry]\n"
                val insertIndex =
                    if (sb.startsWith(desktopHeader)) {
                        desktopHeader.length
                    } else {
                        0
                    }
                sb.insert(insertIndex, "Name=$displayName\n")
                changed = true
            }

            if (changed) {
                com.winlator.cmod.shared.io.FileUtils.writeString(shortcut.file, sb.toString())
            }
        }.onFailure {
            Log.w("SHORTCUTS", "Failed to repair shortcut display name for ${shortcut.file.name}", it)
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
                putExtra(XServerDisplayActivity.EXTRA_LAUNCHED_FROM_PINNED_SHORTCUT, true)
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
            com.winlator.cmod.shared.ui.toast.WinToast.show(
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
        lifecycleScope.launch(Dispatchers.IO) {
            val gameInstallPath = SteamService.getAppDirPath(app.id)
            val gameDir = java.io.File(gameInstallPath)
            if (!gameDir.exists()) {
                withContext(Dispatchers.Main) {
                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                        context,
                        "Game not installed: ${app.name}",
                        android.widget.Toast.LENGTH_SHORT,
                    )
                }
                return@launch
            }

            val shortcut =
                containerManager.loadShortcuts().find {
                    it.getExtra("game_source") == "STEAM" && it.getExtra("app_id") == app.id.toString()
                }
            val detectedLaunchExecutable = SteamService.getInstalledExe(app.id)

            if (shortcut != null) {
                if (!SetupWizardActivity.isContainerUsable(context, shortcut.container)) {
                    withContext(Dispatchers.Main) {
                        SetupWizardActivity.promptToInstallWineOrCreateContainer(
                            context,
                            shortcut.container.wineVersion,
                        )
                    }
                    return@launch
                }
                normalizeContainerDrives(shortcut.container)
                shortcut.putExtra("game_source", "STEAM")
                shortcut.putExtra("game_install_path", gameInstallPath)
                val existingLaunchExecutable = shortcut.getExtra("launch_exe_path")
                if (existingLaunchExecutable.isNullOrBlank() && detectedLaunchExecutable.isNotBlank()) {
                    shortcut.putExtra("launch_exe_path", detectedLaunchExecutable)
                }
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
                withContext(Dispatchers.Main) {
                    launchGame(context, intent)
                }
            } else {
                val container = SetupWizardActivity.getPreferredGameContainer(context, containerManager)

                if (container == null) {
                    withContext(Dispatchers.Main) {
                        SetupWizardActivity.promptToInstallWineOrCreateContainer(context)
                    }
                    return@launch
                }

                normalizeContainerDrives(container)

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
                content.append("launch_exe_path=${detectedLaunchExecutable}\n")
                content.append("use_container_defaults=1\n")

                com.winlator.cmod.shared.io.FileUtils
                    .writeString(shortcutFile, content.toString())

                container.saveData()

                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", container.id)
                intent.putExtra("shortcut_path", shortcutFile.path)
                intent.putExtra("shortcut_name", app.name)
                withContext(Dispatchers.Main) {
                    launchGame(context, intent)
                }
            }
        }
    }

    private fun launchEpicGame(
        context: android.content.Context,
        containerManager: ContainerManager,
        app: EpicGame,
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val gameInstallPath = app.installPath.takeIf { it.isNotEmpty() } ?: EpicConstants.getGameInstallPath(context, app.appName)
            val gameDir = java.io.File(gameInstallPath)
            if (!gameDir.exists()) {
                withContext(Dispatchers.Main) {
                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                        context,
                        "Game not installed: ${app.title}",
                        android.widget.Toast.LENGTH_SHORT,
                    )
                }
                return@launch
            }

            // Try to find an existing shortcut first (preserves per-game settings)
            val existingShortcut =
                containerManager.loadShortcuts().find {
                    it.getExtra("game_source") == "EPIC" && it.getExtra("app_id") == app.id.toString()
                }

            if (existingShortcut != null) {
                val launchContainer =
                    resolveShortcutLaunchContainer(containerManager, existingShortcut)
                        ?: existingShortcut.container
                if (!SetupWizardActivity.isContainerUsable(context, launchContainer)) {
                    withContext(Dispatchers.Main) {
                        SetupWizardActivity.promptToInstallWineOrCreateContainer(
                            context,
                            launchContainer.wineVersion,
                        )
                    }
                    return@launch
                }
                // Existing shortcut found: preserve per-game settings and update the mapped install path
                val shortcut = existingShortcut
                val epicDisplayName =
                    app.title.takeIf { it.isNotBlank() }
                        ?: shortcut.name.takeIf { it.isNotBlank() }
                        ?: app.appName
                // Ensure game_install_path is always up-to-date
                shortcut.putExtra("game_install_path", gameInstallPath)
                shortcut.putExtra("container_id", launchContainer.id.toString())
                repairShortcutDisplayNameIfNeeded(shortcut, epicDisplayName, app.appName, app.id.toString())
                normalizeContainerDrives(launchContainer)

                // Repair broken Exec line if the executable is missing or still points at a legacy placeholder mapping.
                val currentPath = shortcut.path
                if (currentPath == null || currentPath == "D:\\" || currentPath == "D:\\\\" ||
                    currentPath == "A:\\" || currentPath == "A:\\\\" ||
                    currentPath.startsWith("A:\\")
                ) {
                    val newExecCmd =
                        buildStoreWineExecCommandForSelectedExe(
                            launchContainer,
                            "EPIC",
                            gameInstallPath,
                            shortcut.getExtra("launch_exe_path"),
                        ) ?: run {
                            val exePath = EpicService.getInstalledExe(app.id)
                            if (exePath.isNotEmpty()) {
                                shortcut.putExtra("launch_exe_path", exePath)
                                buildStoreWineExecCommand(
                                    launchContainer,
                                    "EPIC",
                                    gameInstallPath,
                                    java.io.File(gameInstallPath, exePath.replace("\\", "/")),
                                )
                            } else {
                                val exeFile = findGameExe(gameDir)
                                if (exeFile != null) {
                                    shortcut.putExtra("launch_exe_path", exeFile.absolutePath)
                                    buildStoreWineExecCommand(launchContainer, "EPIC", gameInstallPath, exeFile)
                                } else {
                                    null
                                }
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
                val launchShortcutFile = ensureShortcutFileInContainer(shortcut, launchContainer)

                // Provision the EOS overlay into this container. Best-effort — failures are
                // non-fatal (games without the EOS SDK ignore it; games with the SDK still run
                // without the in-game HUD). Tokens must be staged inside the prefix because
                // the dosdevices map doesn't expose the app cache dir on any drive letter.
                runCatching {
                    EpicService.installOverlay(context, launchContainer)
                }.onFailure {
                    Log.w("EPIC", "EOS overlay install failed for ${app.appName}; launching anyway", it)
                }

                val launchArgsResult =
                    EpicGameLauncher.buildLaunchParameters(
                        context = context,
                        game = app,
                        container = launchContainer,
                    )
                launchArgsResult.exceptionOrNull()?.let { err ->
                    // The launch can still proceed (offline-tolerant titles, single-player non-DRM
                    // games), so we don't abort — but surface the failure prominently so users
                    // know why a DRM/online title may bounce to its login screen.
                    Log.e("EPIC", "Failed to build Epic launch parameters for ${app.appName}: ${err.message}", err)
                    withContext(Dispatchers.Main) {
                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                            context,
                            "Could not refresh Epic launch token: ${err.message ?: "unknown error"}",
                            android.widget.Toast.LENGTH_LONG,
                        )
                    }
                }
                val args = launchArgsResult.getOrNull()?.joinToString(" ") ?: ""

                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", launchContainer.id)
                intent.putExtra("shortcut_path", launchShortcutFile.path)
                intent.putExtra("shortcut_name", epicDisplayName)
                intent.putExtra("extra_exec_args", args) // Pass fresh tokens
                withContext(Dispatchers.Main) {
                    launchGame(context, intent)
                }
            } else {
                // No existing shortcut — create a new one
                val exePath = EpicService.getInstalledExe(app.id)
                val container = SetupWizardActivity.getPreferredGameContainer(context, containerManager)

                if (container == null) {
                    withContext(Dispatchers.Main) {
                        SetupWizardActivity.promptToInstallWineOrCreateContainer(context)
                    }
                    return@launch
                }

                normalizeContainerDrives(container)
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
                if (app.catalogId.isNotEmpty()) {
                    // Persist catalog_id so EpicGameFixHelper / GameFixes can dispatch the
                    // per-catalog registry/env/folder fixes without a DB round-trip on launch.
                    content.append("catalog_id=${app.catalogId}\n")
                }
                content.append("container_id=${container.id}\n")
                content.append("game_install_path=${gameInstallPath}\n")
                if (exePath.isNotEmpty()) {
                    content.append("launch_exe_path=${exePath}\n")
                }
                content.append("use_container_defaults=1\n")

                com.winlator.cmod.shared.io.FileUtils
                    .writeString(shortcutFile, content.toString())

                container.saveData()

                // Best-effort EOS overlay provisioning — see existing-shortcut branch above.
                runCatching {
                    EpicService.installOverlay(context, container)
                }.onFailure {
                    Log.w("EPIC", "EOS overlay install failed for ${app.appName}; launching anyway", it)
                }

                val launchArgsResult =
                    EpicGameLauncher.buildLaunchParameters(
                        context = context,
                        game = app,
                        container = container,
                    )
                launchArgsResult.exceptionOrNull()?.let { err ->
                    Log.e("EPIC", "Failed to build Epic launch parameters for ${app.appName}: ${err.message}", err)
                    withContext(Dispatchers.Main) {
                        com.winlator.cmod.shared.ui.toast.WinToast.show(
                            context,
                            "Could not refresh Epic launch token: ${err.message ?: "unknown error"}",
                            android.widget.Toast.LENGTH_LONG,
                        )
                    }
                }
                val args = launchArgsResult.getOrNull()?.joinToString(" ") ?: ""

                val intent = Intent(context, XServerDisplayActivity::class.java)
                intent.putExtra("container_id", container.id)
                intent.putExtra("shortcut_path", shortcutFile.path)
                intent.putExtra("shortcut_name", app.title)
                intent.putExtra("extra_exec_args", args) // Pass fresh tokens
                withContext(Dispatchers.Main) {
                    launchGame(context, intent)
                }
            }
        }
    }

    private fun launchGogGame(
        context: android.content.Context,
        containerManager: ContainerManager,
        app: GOGGame,
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val gameInstallPath = app.installPath.takeIf { it.isNotEmpty() } ?: GOGConstants.getGameInstallPath(app.title)
            val gameDir = java.io.File(gameInstallPath)
            if (!gameDir.exists()) {
                withContext(Dispatchers.Main) {
                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                        context,
                        "Game not installed: ${app.title}",
                        android.widget.Toast.LENGTH_SHORT,
                    )
                }
                return@launch
            }

            val existingShortcut =
                containerManager.loadShortcuts().find {
                    it.getExtra("game_source") == "GOG" && it.getExtra("gog_id") == app.id
                }

            val gogAppId = "GOG_${app.id}"
            GOGService.syncCloudSaves(context, gogAppId)

            if (existingShortcut != null) {
                val shortcut = existingShortcut
                if (!SetupWizardActivity.isContainerUsable(context, shortcut.container)) {
                    withContext(Dispatchers.Main) {
                        SetupWizardActivity.promptToInstallWineOrCreateContainer(
                            context,
                            shortcut.container.wineVersion,
                        )
                    }
                    return@launch
                }
                shortcut.putExtra("game_install_path", gameInstallPath)
                normalizeContainerDrives(shortcut.container)

                // Repair broken Exec line if the executable is missing or still points at a legacy placeholder mapping.
                val currentPath = shortcut.path
                if (currentPath == null || currentPath == "D:\\" || currentPath == "D:\\\\" ||
                    currentPath == "A:\\" || currentPath == "A:\\\\" ||
                    currentPath.startsWith("A:\\")
                ) {
                    val newExecCmd =
                        buildStoreWineExecCommandForSelectedExe(
                            shortcut.container,
                            "GOG",
                            gameInstallPath,
                            shortcut.getExtra("launch_exe_path"),
                        ) ?: run {
                            val libraryItem =
                                LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.feature.stores.steam.enums.GameSource.GOG)
                            val exePath = GOGService.getInstalledExe(libraryItem)
                            if (exePath.isNotEmpty()) {
                                shortcut.putExtra("launch_exe_path", exePath)
                                buildStoreWineExecCommand(
                                    shortcut.container,
                                    "GOG",
                                    gameInstallPath,
                                    java.io.File(gameInstallPath, exePath.replace("\\", "/")),
                                )
                            } else {
                                val exeFile = findGameExe(gameDir)
                                if (exeFile != null) {
                                    shortcut.putExtra("launch_exe_path", exeFile.absolutePath)
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
                withContext(Dispatchers.Main) {
                    launchGame(context, intent)
                }
                return@launch
            }

            val libraryItem = LibraryItem("GOG_${app.id}", app.title, com.winlator.cmod.feature.stores.steam.enums.GameSource.GOG)
            val exePath = GOGService.getInstalledExe(libraryItem)

            val container = SetupWizardActivity.getPreferredGameContainer(context, containerManager)

            if (container == null) {
                withContext(Dispatchers.Main) {
                    SetupWizardActivity.promptToInstallWineOrCreateContainer(context)
                }
                return@launch
            }

            normalizeContainerDrives(container)
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
                if (exePath.isNotEmpty()) {
                    content.append("launch_exe_path=${exePath}\n")
                }
                content.append("use_container_defaults=1\n")

            com.winlator.cmod.shared.io.FileUtils
                .writeString(shortcutFile, content.toString())
            container.saveData()

            val intent = Intent(context, XServerDisplayActivity::class.java)
            intent.putExtra("container_id", container.id)
            intent.putExtra("shortcut_path", shortcutFile.path)
            intent.putExtra("shortcut_name", app.title)
            withContext(Dispatchers.Main) {
                launchGame(context, intent)
            }
        }
    }

    private fun normalizeContainerDrives(container: com.winlator.cmod.runtime.container.Container) {
        container.drives =
            com.winlator.cmod.runtime.wine.WineUtils.normalizePersistentDrives(
                this,
                container.drives ?: com.winlator.cmod.runtime.container.Container.DEFAULT_DRIVES,
            )
    }

    private fun resolveShortcutLaunchContainer(
        containerManager: ContainerManager,
        shortcut: Shortcut,
    ): com.winlator.cmod.runtime.container.Container? {
        val overrideContainerId = shortcut.getExtra("container_id").toIntOrNull()?.takeIf { it > 0 }
        return overrideContainerId
            ?.let { containerManager.getContainerById(it) }
            ?: shortcut.container
    }

    private fun ensureShortcutFileInContainer(
        shortcut: Shortcut,
        targetContainer: com.winlator.cmod.runtime.container.Container,
    ): java.io.File {
        val targetDesktopDir = targetContainer.getDesktopDir()
        val alreadyInTarget =
            runCatching {
                shortcut.file.parentFile?.canonicalFile == targetDesktopDir.canonicalFile
            }.getOrDefault(false)

        if (alreadyInTarget) return shortcut.file

        if (!targetDesktopDir.exists()) targetDesktopDir.mkdirs()
        shortcut.putExtra("container_id", targetContainer.id.toString())
        shortcut.saveData()

        val targetFile = java.io.File(targetDesktopDir, shortcut.file.name)
        runCatching {
            com.winlator.cmod.shared.io.FileUtils.copy(shortcut.file, targetFile)
            val lnkFileName = shortcut.file.name.substringBeforeLast(".desktop") + ".lnk"
            val oldLnkFile = java.io.File(shortcut.file.parentFile, lnkFileName)
            if (oldLnkFile.exists()) {
                com.winlator.cmod.shared.io.FileUtils.copy(oldLnkFile, java.io.File(targetDesktopDir, lnkFileName))
                oldLnkFile.delete()
            }
            shortcut.file.delete()
        }.onFailure {
            Log.w("EPIC", "Failed to move Epic shortcut ${shortcut.file.name} to container ${targetContainer.id}; launching original file", it)
            return shortcut.file
        }

        return targetFile
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
                    .getDriveCGameWindowsPath(
                        it,
                        "CUSTOM",
                        gameInstallPath,
                        exeFile.absolutePath,
                    ) ?: com.winlator.cmod.runtime.wine.WineUtils
                    .getWindowsPath(it, exeFile.absolutePath)
            } ?: run {
                com.winlator.cmod.runtime.wine.WineUtils.getDosPath(exeFile.absolutePath)
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
                val linkName =
                    com.winlator.cmod.runtime.wine.WineUtils.getDriveCGameLinkName(gameInstallPath)
                "C:\\WinNative\\Games\\$source\\$linkName\\$relativePath"
        }
        return "wine \"$windowsPath\""
    }

    private fun buildStoreWineExecCommandForSelectedExe(
        container: com.winlator.cmod.runtime.container.Container?,
        source: String,
        gameInstallPath: String,
        selectedExePath: String?,
    ): String? {
        if (selectedExePath.isNullOrBlank()) return null

        val selectedExe = java.io.File(selectedExePath)
        if (!selectedExe.isFile) return null

        val normalizedBaseDir =
            java.io
                .File(gameInstallPath)
                .absolutePath
                .removeSuffix("/")
        val normalizedExePath = selectedExe.absolutePath
        return if (normalizedExePath == normalizedBaseDir || normalizedExePath.startsWith("$normalizedBaseDir/")) {
            buildStoreWineExecCommand(container, source, gameInstallPath, selectedExe)
        } else {
            val hostPath = normalizedExePath.replace("/", "\\\\").let { if (it.startsWith("\\")) it else "\\$it" }
            "wine \"Z:${hostPath}\""
        }
    }

    // Launch custom game by shortcut name
    private fun launchCustomGame(
        context: android.content.Context,
        containerManager: ContainerManager,
        gameName: String,
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
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
                withContext(Dispatchers.Main) {
                    com.winlator.cmod.shared.ui.toast.WinToast.show(
                        context,
                        "Custom game shortcut not found: $gameName",
                        android.widget.Toast.LENGTH_SHORT,
                    )
                }
                return@launch
            }

            // Backfill custom_name if missing (legacy shortcuts)
            if (shortcut.getExtra("custom_name").isEmpty()) {
                shortcut.putExtra("custom_name", gameName)
                shortcut.saveData()
            }

            // Refresh storage-root mappings; custom game paths launch through the drive_c game symlink.
            val gameFolder = shortcut.getExtra("custom_game_folder", "")
            if (gameFolder.isNotEmpty()) {
                normalizeContainerDrives(shortcut.container)
                shortcut.container.saveData()
            }
            val intent = Intent(context, XServerDisplayActivity::class.java)
            intent.putExtra("container_id", shortcut.container.id)
            intent.putExtra("shortcut_path", shortcut.file.path)
            intent.putExtra("shortcut_name", gameName)
            withContext(Dispatchers.Main) {
                launchGame(context, intent)
            }
        }
    }

    private fun launchGame(
        context: android.content.Context,
        intent: Intent,
    ) {
        DownloadService.clearCompletedDownloads()
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
        immersiveMode: Boolean,
        immersiveBlur: Boolean,
        onLibraryLayoutSelected: (LibraryLayoutMode) -> Unit,
        onStoreVisibleChanged: (String, Boolean) -> Unit,
        onContentFiltersChanged: (String, Boolean) -> Unit,
        onImmersiveModeChanged: (Boolean) -> Unit,
        onImmersiveBlurChanged: (Boolean) -> Unit,
        onExitApp: () -> Unit,
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

                // ── View Options ──
                Text(
                    stringResource(R.string.library_games_view_options_header),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(Modifier.height(8.dp))

                DrawerSwitchCard(
                    label = stringResource(R.string.library_games_immersive_mode),
                    description = stringResource(R.string.library_games_immersive_mode_description),
                    checked = immersiveMode,
                    onCheckedChange = onImmersiveModeChanged,
                )

                AnimatedVisibility(visible = immersiveMode) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        DrawerSwitchCard(
                            label = stringResource(R.string.library_games_immersive_blur),
                            description = stringResource(R.string.library_games_immersive_blur_description),
                            checked = immersiveBlur,
                            onCheckedChange = onImmersiveBlurChanged,
                        )
                    }
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
                    DrawerFilterButton("Steam", storeVisible["steam"] == true, Modifier.weight(1f)) { onStoreVisibleChanged("steam", it) }
                    DrawerFilterButton("Epic", storeVisible["epic"] == true, Modifier.weight(1f)) { onStoreVisibleChanged("epic", it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton("GOG", storeVisible["gog"] == true, Modifier.weight(1f)) { onStoreVisibleChanged("gog", it) }
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
                    DrawerFilterButton("Games", contentFilters["games"] == true, Modifier.weight(1f)) { onContentFiltersChanged("games", it) }
                    DrawerFilterButton("DLC", contentFilters["dlc"] == true, Modifier.weight(1f)) { onContentFiltersChanged("dlc", it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerFilterButton("Applications", contentFilters["applications"] == true, Modifier.weight(1f)) { onContentFiltersChanged("applications", it) }
                    DrawerFilterButton("Tools", contentFilters["tools"] == true, Modifier.weight(1f)) { onContentFiltersChanged("tools", it) }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.15f))
                Spacer(Modifier.height(16.dp))

                DrawerExitAppCard(onClick = onExitApp)
            }
        }
    }

    @Composable
    private fun DrawerExitAppCard(onClick: () -> Unit) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = tween(100),
            label = "exitAppCardScale",
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(DangerRed.copy(alpha = 0.16f))
                    .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DangerRed.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFFFFB4B4),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.common_ui_exit_app),
                color = Color(0xFFFFD6D6),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

    @Composable
    private fun DrawerSwitchCard(
        label: String,
        description: String?,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        val bgColor by animateColorAsState(
            targetValue = if (checked) Accent.copy(alpha = 0.18f) else CardDark,
            animationSpec = tween(200),
            label = "switchCardBg",
        )
        val borderColor by animateColorAsState(
            targetValue = if (checked) Accent else CardBorder,
            animationSpec = tween(200),
            label = "switchCardBorder",
        )
        val labelColor by animateColorAsState(
            targetValue = if (checked) Accent else TextPrimary,
            animationSpec = tween(200),
            label = "switchCardLabel",
        )
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = tween(120),
            label = "switchCardScale",
        )

        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onCheckedChange(!checked) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        checkedBorderColor = Accent,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = CardDark,
                        uncheckedBorderColor = CardBorder,
                    ),
            )
        }
    }

    @Composable
    private fun AddCustomGameDialog(onDismiss: () -> Unit) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var selectedExePath by remember { mutableStateOf<String?>(null) }
        var gameName by remember { mutableStateOf("") }
        var gameFolder by remember { mutableStateOf<String?>(null) }
        var isAdding by remember { mutableStateOf(false) }

        fun selectExecutable(path: String) {
            if (!path.endsWith(".exe", ignoreCase = true) || !java.io.File(path).isFile) {
                com.winlator.cmod.shared.ui.toast.WinToast.show(
                    context,
                    R.string.common_ui_select_valid_exe_file,
                    android.widget.Toast.LENGTH_SHORT,
                )
                return
            }

            selectedExePath = path
            gameFolder = LibraryShortcutUtils.detectCustomGameFolder(path)
            // Auto-generate a game name from the EXE name (without extension)
            if (gameName.isBlank()) {
                gameName =
                    java.io
                        .File(path)
                        .nameWithoutExtension
                        .replace("_", " ")
                        .replace("-", " ")
            }
        }

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
                                            DirectoryPickerDialog.showFile(
                                                activity = this@UnifiedActivity,
                                                initialPath = selectedExePath ?: gameFolder,
                                                title = getString(R.string.common_ui_select_exe),
                                                allowedExtensions = setOf("exe"),
                                                dimAmount = 0.5f,
                                                preserveBackdropBlur = true,
                                                onSelected = ::selectExecutable,
                                            )
                                        }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    selectedExePath ?: "Select Executable (.exe)",
                                    color = if (selectedExePath == null) TextSecondary else TextPrimary,
                                    maxLines = if (selectedExePath == null) 1 else Int.MAX_VALUE,
                                    overflow = if (selectedExePath == null) TextOverflow.Ellipsis else TextOverflow.Visible,
                                    fontSize = if (selectedExePath == null) 12.sp else 10.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            if (selectedExePath != null) {

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
                                        Text(
                                            stringResource(R.string.library_games_game_folder_mapped_drive),
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                        )
                                        Text(
                                            gameFolder ?: stringResource(R.string.common_ui_auto_detected),
                                            color = if (gameFolder != null) TextPrimary else TextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(onClick = {
                                        if (!ensureAllFilesAccessForImports(context)) return@IconButton
                                        DirectoryPickerDialog.show(
                                            activity = this@UnifiedActivity,
                                            initialPath = gameFolder,
                                            title = getString(R.string.common_ui_select_folder),
                                            dimAmount = 0.5f,
                                            preserveBackdropBlur = true,
                                        ) { path -> gameFolder = path }
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = stringResource(R.string.common_ui_change),
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
                                        com.winlator.cmod.shared.ui.toast.WinToast.show(
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
                                            com.winlator.cmod.shared.ui.toast.WinToast.show(
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

    private fun ensureAllFilesAccessForImports(context: android.content.Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()) {
            return true
        }

        com.winlator.cmod.shared.ui.toast.WinToast.show(
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
        normalizeContainerDrives(container)
        val execCmd = buildWineExecCommand(container, gameFolderPath, exeFile)

        val desktopDir = container.getDesktopDir()
        if (!desktopDir.exists()) desktopDir.mkdirs()
        val safeName = name.replace("/", "_").replace("\\", "_")
        val shortcutFile = java.io.File(desktopDir, "$safeName.desktop")
        val shortcutUuid = java.util.UUID.randomUUID().toString()
        val iconOutFile = LibraryShortcutArtwork.buildManagedCustomGameArtworkFile(context, shortcutUuid)
        val extractedArtworkPath =
            try {
                if (PeIconExtractor.extractAndSave(java.io.File(exePath), iconOutFile)) {
                    iconOutFile.absolutePath
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
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
        content.append("uuid=$shortcutUuid\n")
        extractedArtworkPath?.let { content.append("customCoverArtPath=$it\n") }
        content.append("container_id=${container.id}\n")
        content.append("use_container_defaults=1\n")
        com.winlator.cmod.shared.io.FileUtils
            .writeString(shortcutFile, content.toString())
        container.saveData()
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

    @Composable
    private fun rememberControllerConnectionState(): ControllerConnectionState {
        val context = LocalContext.current
        val inputManager = remember(context) { context.getSystemService(InputManager::class.java) }
        var controllerState by remember { mutableStateOf(ControllerConnectionState()) }

        DisposableEffect(inputManager) {
            fun refreshState() {
                controllerState =
                    ControllerConnectionState(
                        isConnected = ControllerHelper.isControllerConnected(),
                        isPlayStation = ControllerHelper.isPlayStationController(),
                    )
            }

            val listener =
                object : InputManager.InputDeviceListener {
                    override fun onInputDeviceAdded(deviceId: Int) = refreshState()

                    override fun onInputDeviceRemoved(deviceId: Int) = refreshState()

                    override fun onInputDeviceChanged(deviceId: Int) = refreshState()
                }

            refreshState()
            inputManager?.registerInputDeviceListener(listener, null)
            onDispose {
                inputManager?.unregisterInputDeviceListener(listener)
            }
        }

        return controllerState
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

private inline fun android.content.Context.runIfOnlineOrToast(action: () -> Unit) {
    if (com.winlator.cmod.app.service.NetworkMonitor.hasInternet.value) {
        action()
    } else {
        com.winlator.cmod.shared.ui.toast.WinToast.show(
            this,
            getString(R.string.downloads_no_internet),
            android.widget.Toast.LENGTH_SHORT,
        )
    }
}
