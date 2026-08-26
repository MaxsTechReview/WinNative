package com.winlator.cmod.feature.settings
import android.os.Bundle
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.winlator.cmod.R
import com.winlator.cmod.feature.sync.google.GoogleFragment
import com.winlator.cmod.shared.ui.nav.PANE_DIR_ACTIVATE
import com.winlator.cmod.shared.ui.nav.PANE_DIR_DOWN
import com.winlator.cmod.shared.ui.nav.PANE_DIR_LEFT
import com.winlator.cmod.shared.ui.nav.PANE_DIR_RIGHT
import com.winlator.cmod.shared.ui.nav.PANE_DIR_SECONDARY
import com.winlator.cmod.shared.ui.nav.PANE_DIR_UP

object SettingsRoutes {
    fun fromNavItem(item: SettingsNavItem): String = "settings/${item.name.lowercase()}"
}

enum class SettingsFocusZone { SIDEBAR, CONTENT }

class SettingsNavBridge {
    var selectedItem by mutableStateOf(SettingsNavItem.CONTAINERS)
    var zone by mutableStateOf(SettingsFocusZone.SIDEBAR)
    var onSelectItem: ((SettingsNavItem) -> Unit)? = null

    var contentControllerActive by mutableStateOf(false)
    var contentNavSignal by mutableStateOf(0)
        private set
    var contentNavDir by mutableStateOf(0)
        private set

    private fun contentNav(dir: Int) {
        contentNavDir = dir
        contentNavSignal++
    }

    fun contentNavLeft() = contentNav(PANE_DIR_LEFT)

    fun contentNavRight() = contentNav(PANE_DIR_RIGHT)

    fun contentNavUp() = contentNav(PANE_DIR_UP)

    fun contentNavDown() = contentNav(PANE_DIR_DOWN)

    fun contentActivate() = contentNav(PANE_DIR_ACTIVATE)

    fun contentSecondary() = contentNav(PANE_DIR_SECONDARY)

    var contentSectionSignal by mutableStateOf(0)
        private set
    var contentSectionDir by mutableStateOf(0)
        private set

    private fun contentSection(dir: Int) {
        contentSectionDir = dir
        contentSectionSignal++
    }

    fun contentSectionPrev() = contentSection(-1)

    fun contentSectionNext() = contentSection(1)
}

private val SettingsBg = Color(0xFF11111C)

@Composable
fun SettingsHost(
    bridge: SettingsNavBridge,
    startItem: SettingsNavItem = SettingsNavItem.CONTAINERS,
    selectedProfileId: Int = 0,
    bordersPaused: Boolean = false,
    onBack: () -> Unit,
) {
    val settingsNavController = rememberNavController()
    var currentItem by rememberSaveable { mutableStateOf(startItem) }

    // On a narrow (phone-portrait) width, the fixed 220dp sidebar plus content side by
    // side leaves the content pane too cramped to be usable. Below that width we collapse
    // to a single pane: show the nav list full-screen first, then swap to the selected
    // section full-screen with its own back arrow — a standard master/detail collapse.
    val settingsScreenWidth = LocalConfiguration.current.screenWidthDp.dp
    val isNarrowSettings = settingsScreenWidth < 480.dp
    var showingContentOnNarrow by rememberSaveable { mutableStateOf(false) }

    val navigateTo: (SettingsNavItem) -> Unit = { item ->
        if (item != currentItem) {
            currentItem = item
            settingsNavController.navigate(SettingsRoutes.fromNavItem(item)) {
                popUpTo(SettingsRoutes.fromNavItem(startItem)) {
                    inclusive = false
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    SideEffect { bridge.selectedItem = currentItem }

    DisposableEffect(Unit) {
        bridge.zone = SettingsFocusZone.SIDEBAR
        bridge.onSelectItem = navigateTo
        onDispose { bridge.onSelectItem = null }
    }

    // IMPORTANT: within the narrow (portrait) layout, SettingsContentNavHost — which
    // hosts AndroidFragment<...> for every settings section — now has exactly ONE call
    // site, used for both the "master list showing" and "section showing" states.
    // Previously those were two different if/else branches, which Compose treats as
    // different subtrees: every tap going from the master list into a section, or back,
    // fully disposed and recreated the NavHost and all its Fragments. That churn is a
    // well-known source of freezes/ANRs in Compose+Fragment interop (fragment
    // transactions and view teardown fighting the next composition on the main
    // thread). Now the content pane stays permanently composed underneath and the
    // master list is just an overlay on top of it, so Fragments survive repeated
    // navigation in and out of a section. (Switching between narrow and wide layout,
    // e.g. on rotation, is a separate, much less frequent event and still uses its own
    // call site below — that trade-off is intentional.)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(SettingsBg),
    ) {
        if (isNarrowSettings) {
            Column(modifier = Modifier.fillMaxSize()) {
                NarrowSettingsContentBackBar(
                    title = stringResource(currentItem.titleRes),
                    onBack = {
                        bridge.zone = SettingsFocusZone.SIDEBAR
                        showingContentOnNarrow = false
                    },
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                // Only track taps for focus-zone switching while this
                                // pane is actually the visible one. It stays composed
                                // underneath the master-list overlay (see below) so its
                                // Fragments survive, but Initial-pass pointer input
                                // fires on any tap within these bounds regardless of
                                // what's drawn on top — without this guard, tapping a
                                // sidebar item while the overlay is showing would also
                                // reach this listener since the two occupy the same
                                // screen region.
                                if (showingContentOnNarrow) {
                                    Modifier.pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                                if (ev.type == PointerEventType.Press) {
                                                    bridge.zone = SettingsFocusZone.CONTENT
                                                    bridge.contentControllerActive = false
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                ) {
                    SettingsContentNavHost(
                        navController = settingsNavController,
                        startItem = startItem,
                        selectedProfileId = selectedProfileId,
                        bridge = bridge,
                    )
                }
            }

            // Master list overlays the content pane full-screen until a section is
            // picked. The content behind it stays composed the whole time — this is
            // just a visual layer on top, not a structural swap.
            if (!showingContentOnNarrow) {
                SettingsNavSidebar(
                    selectedItem = currentItem,
                    railActive = bridge.zone == SettingsFocusZone.SIDEBAR,
                    onItemSelected = { item ->
                        navigateTo(item)
                        bridge.zone = SettingsFocusZone.CONTENT
                        showingContentOnNarrow = true
                    },
                    onBackPressed = onBack,
                    bordersPaused = bordersPaused,
                    columnWidthModifier = Modifier.fillMaxWidth(),
                    showEdgeDivider = false,
                )
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                SettingsNavSidebar(
                    selectedItem = currentItem,
                    railActive = bridge.zone == SettingsFocusZone.SIDEBAR,
                    onItemSelected = { item ->
                        bridge.zone = SettingsFocusZone.SIDEBAR
                        navigateTo(item)
                    },
                    onBackPressed = onBack,
                    bordersPaused = bordersPaused,
                )

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                        if (ev.type == PointerEventType.Press) {
                                            bridge.zone = SettingsFocusZone.CONTENT
                                            bridge.contentControllerActive = false
                                        }
                                    }
                                }
                            },
                ) {
                    SettingsContentNavHost(
                        navController = settingsNavController,
                        startItem = startItem,
                        selectedProfileId = selectedProfileId,
                        bridge = bridge,
                    )
                }
            }
        }
    }
}

@Composable
private fun NarrowSettingsContentBackBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 10.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.common_ui_back),
            tint = Color(0xFFFFB74D),
            modifier = Modifier.size(22.dp),
        )
        Text(
            title,
            color = Color(0xFFF5F0EA),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SettingsContentNavHost(
    navController: androidx.navigation.NavHostController,
    startItem: SettingsNavItem,
    selectedProfileId: Int,
    bridge: SettingsNavBridge,
) {
    NavHost(
        navController = navController,
        startDestination = SettingsRoutes.fromNavItem(startItem),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(SettingsRoutes.fromNavItem(SettingsNavItem.CONTAINERS)) {
            AndroidFragment<ContainersFragment>()
        }
        composable(SettingsRoutes.fromNavItem(SettingsNavItem.INPUT_CONTROLS)) {
            AndroidFragment<InputControlsFragment>(
                arguments =
                    Bundle().apply {
                        putInt("selectedProfileId", selectedProfileId)
                    },
            )
        }
        composable(SettingsRoutes.fromNavItem(SettingsNavItem.COMPONENTS)) {
            AndroidFragment<ContentsFragment>()
        }
        composable(SettingsRoutes.fromNavItem(SettingsNavItem.DRIVERS)) {
            AndroidFragment<DriversFragment>()
        }
        composable(SettingsRoutes.fromNavItem(SettingsNavItem.STORES)) {
            AndroidFragment<StoresFragment>()
        }
        composable(SettingsRoutes.fromNavItem(SettingsNavItem.DEBUG)) {
            AndroidFragment<DebugFragment>()
        }
        composable(SettingsRoutes.fromNavItem(SettingsNavItem.GOOGLE)) {
            AndroidFragment<GoogleFragment>()
        }
        composable(SettingsRoutes.fromNavItem(SettingsNavItem.PRESETS)) {
            AndroidFragment<PresetsFragment>()
        }
        composable(SettingsRoutes.fromNavItem(SettingsNavItem.OTHER)) {
            AndroidFragment<OtherSettingsFragment>()
        }
        composable(SettingsRoutes.fromNavItem(SettingsNavItem.CREDITS)) {
            com.winlator.cmod.feature.settings.WinNativeCreditsScreen(bridge = bridge)
        }
    }
}
