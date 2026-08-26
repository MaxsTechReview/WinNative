package com.winlator.cmod.app.shell
import com.winlator.cmod.app.shell.UnifiedActivity.ArtworkCacheId

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

// Shortcut lookup + artwork resolution + home-screen pinning, split out of UnifiedActivity.kt (behavior-identical).

internal fun UnifiedActivity.findLibraryShortcutForGame(
    containerManager: ContainerManager,
    app: SteamApp,
    isCustom: Boolean,
    isEpic: Boolean,
    epicId: Int,
): Shortcut? = findShortcutForGame(containerManager.loadShortcuts(), app, isCustom, isEpic, epicId)


internal fun UnifiedActivity.findShortcutForGame(
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


internal fun UnifiedActivity.findLibraryArtworkShortcut(
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


internal fun UnifiedActivity.artworkCacheId(
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


internal fun UnifiedActivity.customArtworkOverrideSlots(
    app: SteamApp,
    gogGame: GOGGame?,
    epicGame: EpicGame?,
    hasDefaultCustomArt: Boolean,
    hasIconCustomArt: Boolean,
    hasHeroCustomArt: Boolean,
): Set<String> {
    val overridesPrimary = hasDefaultCustomArt || hasIconCustomArt
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
                if (overridesPrimary) {
                    add("capsule")
                    add("library_capsule")
                    add("small_capsule")
                }
                if (hasHeroCustomArt) add("hero")
            }
        }

        else -> emptySet()
    }
}

internal fun UnifiedActivity.isShortcutCloudSyncEnabled(shortcut: Shortcut?): Boolean =
    shortcut == null || shortcut.getExtra("cloud_sync_disabled", "0") != "1"


internal fun UnifiedActivity.setShortcutCloudSyncEnabled(
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

internal fun UnifiedActivity.isShortcutOfflineMode(shortcut: Shortcut?): Boolean =
    shortcut != null && shortcut.getExtra("offline_mode", "0") == "1"


internal fun UnifiedActivity.setShortcutOfflineMode(
    shortcut: Shortcut?,
    enabled: Boolean,
) {
    if (shortcut == null) return
    shortcut.putExtra("offline_mode", if (enabled) "1" else null)
    shortcut.saveData()
}

internal fun UnifiedActivity.repairShortcutDisplayNameIfNeeded(
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

internal fun UnifiedActivity.resolveLibraryShortcutArtworkModel(
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


internal suspend fun UnifiedActivity.loadArtworkBitmap(
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

internal suspend fun UnifiedActivity.requestPinnedHomeShortcut(
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

internal suspend fun UnifiedActivity.addLibraryShortcutToHomeScreen(
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

internal suspend fun UnifiedActivity.addGogShortcutToHomeScreen(
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
