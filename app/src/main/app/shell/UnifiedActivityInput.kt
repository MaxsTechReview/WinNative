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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.winlator.cmod.feature.settings.SettingsFocusZone
import com.winlator.cmod.feature.settings.SettingsHost
import com.winlator.cmod.feature.settings.SettingsNavBridge
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
import androidx.compose.foundation.focusGroup
import com.winlator.cmod.shared.ui.focus.controllerFocusGlow
import com.winlator.cmod.shared.ui.focus.controllerMenuInput
import com.winlator.cmod.shared.ui.focus.controllerTextFieldEscape
import com.winlator.cmod.shared.ui.nav.DialogPaneNav
import com.winlator.cmod.shared.ui.nav.LocalPaneNav
import com.winlator.cmod.shared.ui.nav.PANE_DIR_ACTIVATE
import com.winlator.cmod.shared.ui.nav.PANE_DIR_DOWN
import com.winlator.cmod.shared.ui.nav.PANE_DIR_LEFT
import com.winlator.cmod.shared.ui.nav.PANE_DIR_RIGHT
import com.winlator.cmod.shared.ui.nav.PANE_DIR_SECONDARY
import com.winlator.cmod.shared.ui.nav.PANE_DIR_UP
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry
import com.winlator.cmod.shared.ui.nav.paneNavItem
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

// Gamepad/key nav dispatch + glasses combo + immersive bars, split out of UnifiedActivity.kt (behavior-identical).

internal fun UnifiedActivity.updateGlassesCombo() {
    val l2 = l2KeyDown || l2AxisDown
    val r2 = r2KeyDown || r2AxisDown
    if (l2 && r2) {
        if (glassesComboArmed) {
            glassesComboArmed = false
            if (com.winlator.cmod.runtime.display.GlassesManager.isConnected()) {
                openGlassesSignal.tryEmit(Unit)
            }
        }
    } else {
        glassesComboArmed = true
    }
}

internal fun UnifiedActivity.applyImmersiveSystemBars(enabled: Boolean) {
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}

internal fun UnifiedActivity.moveLibraryFocus(
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

internal fun UnifiedActivity.moveStoreFocus(
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

internal fun UnifiedActivity.routeDownloadsNav(
    left: Boolean,
    right: Boolean,
    up: Boolean,
    down: Boolean,
) {
    downloadsNavBridge.controllerActive = true
    when {
        left -> downloadsNavBridge.left()
        right -> downloadsNavBridge.right()
        up -> downloadsNavBridge.up()
        down -> downloadsNavBridge.down()
    }
}

internal fun UnifiedActivity.gogPseudoId(gameId: String): Int {
    val normalized = gameId.hashCode() and 0x1FFFFFFF
    return 1_500_000_000 + normalized
}

internal fun UnifiedActivity.injectKeyEvent(keyCode: Int) {
    window.decorView.rootView.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
    window.decorView.rootView.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
}

internal fun UnifiedActivity.hideImeIfVisible(): Boolean {
    val decor = window.decorView
    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor) ?: return false
    if (!insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())) return false
    val target = currentFocus ?: decor
    androidx.core.view.WindowInsetsControllerCompat(window, target)
        .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
    return true
}

internal fun UnifiedActivity.applySettingsSidebarNav(keyCode: Int) {
    when (keyCode) {
        android.view.KeyEvent.KEYCODE_DPAD_UP -> moveSettingsItem(-1)
        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> moveSettingsItem(1)
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> enterSettingsContent()
    }
}

internal fun UnifiedActivity.moveSettingsItem(delta: Int) {
    val items = SettingsNavItem.entries
    val index = items.indexOf(settingsNavBridge.selectedItem)
    val next = index + delta
    if (next in items.indices) settingsNavBridge.onSelectItem?.invoke(items[next])
}

internal fun UnifiedActivity.enterSettingsContent() {
    settingsNavBridge.zone = SettingsFocusZone.CONTENT
    settingsNavBridge.contentControllerActive = true
}

internal fun UnifiedActivity.navigateSettingsContent(code: Int) {
    settingsNavBridge.contentControllerActive = true
    when (code) {
        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> settingsNavBridge.contentNavLeft()
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> settingsNavBridge.contentNavRight()
        android.view.KeyEvent.KEYCODE_DPAD_UP -> settingsNavBridge.contentNavUp()
        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> settingsNavBridge.contentNavDown()
    }
}

internal fun UnifiedActivity.findVisibleFragmentContainer(view: android.view.View): android.view.View? {
    if (view is androidx.fragment.app.FragmentContainerView && view.isShown && view.childCount > 0) {
        return view
    }
    if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            findVisibleFragmentContainer(view.getChildAt(i))?.let { return it }
        }
    }
    return null
}

internal fun UnifiedActivity.handleSettingsStick(code: Int) {
    if (settingsNavBridge.zone == SettingsFocusZone.SIDEBAR) {
        applySettingsSidebarNav(code)
        return
    }
    navigateSettingsContent(code)
}

internal fun UnifiedActivity.handleGuideButton(action: Int, repeatCount: Int) {
    when (action) {
        android.view.KeyEvent.ACTION_DOWN -> {
            if (repeatCount != 0) return
            guideHoldRunnable?.let { guideHandler.removeCallbacks(it) }
            guideHoldRunnable = null
            if (rightDrawerOpen) {
                val r = Runnable { openFriendsSignal.tryEmit(Unit) }
                guideHoldRunnable = r
                guideHandler.postDelayed(r, 400L)
            } else if (!menuNavActive && !drawerOpen) {
                openFriendsSignal.tryEmit(Unit)
            }
        }

        android.view.KeyEvent.ACTION_UP -> {
            guideHoldRunnable?.let { guideHandler.removeCallbacks(it) }
            guideHoldRunnable = null
        }
    }
}

internal fun UnifiedActivity.reapplyPreferredRefreshRate() {
    if (isFinishing || isDestroyed) return
    RefreshRateUtils.applyPreferredRefreshRate(this)
}
