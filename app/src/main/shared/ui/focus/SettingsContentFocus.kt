package com.winlator.cmod.shared.ui.focus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.winlator.cmod.feature.settings.SettingsFocusZone
import com.winlator.cmod.feature.settings.SettingsNavBridge
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry

@Composable
internal fun rememberSettingsContentNav(bridge: SettingsNavBridge?): PaneNavRegistry {
    val registry = remember(bridge) { PaneNavRegistry(initialSignal = bridge?.contentNavSignal ?: -1) }
    registry.controllerActive =
        (bridge?.contentControllerActive ?: false) && bridge?.zone == SettingsFocusZone.CONTENT
    registry.onEdgeLeft = { bridge?.zone = SettingsFocusZone.SIDEBAR }
    LaunchedEffect(registry, bridge?.contentNavSignal) {
        registry.processNav(bridge?.contentNavSignal ?: 0, bridge?.contentNavDir ?: 0)
    }
    return registry
}

fun Modifier.settingsContentRoot(bridge: SettingsNavBridge?): Modifier = this
