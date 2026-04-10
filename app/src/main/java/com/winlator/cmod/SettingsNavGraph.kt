package com.winlator.cmod

import android.os.Bundle
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.fragment.compose.AndroidFragment
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.winlator.cmod.google.GoogleFragment

object SettingsRoutes {
    fun fromNavItem(item: SettingsNavItem): String = "settings/${item.name.lowercase()}"
}

private val SettingsBg = Color(0xFF1C1C1C)

@Composable
fun SettingsHost(
    startItem: SettingsNavItem = SettingsNavItem.CONTAINERS,
    selectedProfileId: Int = 0,
    bordersPaused: Boolean = false,
    onBack: () -> Unit
) {
    val settingsNavController = rememberNavController()
    var currentItem by rememberSaveable { mutableStateOf(startItem) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .background(SettingsBg)
    ) {
            SettingsNavSidebar(
                selectedItem = currentItem,
                onItemSelected = { item ->
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
                },
                onBackPressed = onBack,
                bordersPaused = bordersPaused
            )

            NavHost(
                navController = settingsNavController,
                startDestination = SettingsRoutes.fromNavItem(startItem),
                enterTransition = { fadeIn(tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)) },
                popEnterTransition = { fadeIn(tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)) },
                popExitTransition = { fadeOut(tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                composable(SettingsRoutes.fromNavItem(SettingsNavItem.CONTAINERS)) {
                    AndroidFragment<ContainersFragment>()
                }
                composable(SettingsRoutes.fromNavItem(SettingsNavItem.INPUT_CONTROLS)) {
                    AndroidFragment<InputControlsFragment>(
                        arguments = Bundle().apply {
                            putInt("selectedProfileId", selectedProfileId)
                        }
                    )
                }
                composable(SettingsRoutes.fromNavItem(SettingsNavItem.COMPONENTS)) {
                    AndroidFragment<ContentsFragment>()
                }
                composable(SettingsRoutes.fromNavItem(SettingsNavItem.DRIVERS)) {
                    AndroidFragment<AdrenotoolsFragment>()
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
            }
        }
}
