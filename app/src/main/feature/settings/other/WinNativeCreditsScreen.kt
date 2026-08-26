package com.winlator.cmod.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.shared.ui.focus.rememberSettingsContentNav
import com.winlator.cmod.shared.ui.nav.LocalPaneNav
import com.winlator.cmod.shared.ui.nav.paneNavItem

private val CreditsBg = Color(0xFF101018)
private val CreditsText = Color(0xFFF5F0EA)
private val CreditsSub = Color(0xFFC7A88F)

/**
 * Credits screen for WinNative's PC/Windows compatibility layer components
 * (Wine, Box86/64, FEX, drivers, etc). The console/retro emulator credits
 * screen was removed along with that feature set.
 */
@Composable
fun WinNativeCreditsScreen(bridge: SettingsNavBridge? = null) {
    val context = LocalContext.current
    val contentNav = rememberSettingsContentNav(bridge)

    fun open(url: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url),
                ),
            )
        }
    }

    CompositionLocalProvider(LocalPaneNav provides contentNav) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(CreditsBg)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.retro_scr_credits_licenses),
                color = CreditsSub,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            WINNATIVE_CREDITS.forEach { credit ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { open(credit.url) }
                            .paneNavItem(
                                cornerRadius = 8.dp,
                                onActivate = { open(credit.url) },
                                highlightColor = Color(0xFFFFB74D),
                                tapToSelect = true,
                            )
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(credit.name, color = CreditsText, style = MaterialTheme.typography.bodyMedium)
                        Text(credit.detail, color = CreditsSub, fontSize = 11.sp)
                    }
                    Text(credit.license, color = CreditsSub, fontSize = 11.sp)
                }
            }
        }
    }
}

internal data class WinNativeCredit(
    val name: String,
    val detail: String,
    val license: String,
    val url: String,
)

internal val WINNATIVE_CREDITS =
    listOf(
        WinNativeCredit("Wine", "Windows compatibility layer", "LGPL-2.1", "https://gitlab.winehq.org/wine/wine"),
        WinNativeCredit("Box86/Box64", "x86/x86_64 dynarec", "MIT", "https://github.com/ptitSeb/box64"),
        WinNativeCredit("FEX-Emu", "x86/x86_64 emulation core", "MIT", "https://github.com/FEX-Emu/FEX"),
        WinNativeCredit("Winlator", "Windows-on-Android base", "GPL-3.0", "https://github.com/brunodev85/winlator"),
        WinNativeCredit("adrenotools", "GPU driver loading", "MIT", "https://github.com/Pipetto-crypto/libadrenotools"),
        WinNativeCredit("vkBasalt", "Vulkan post-processing", "MIT", "https://github.com/WinNative-Emu/vkBasalt"),
        WinNativeCredit("DXVK / VKD3D-Proton", "DirectX-to-Vulkan translation", "zlib / LGPL-2.1", "https://github.com/doitsujin/dxvk"),
        WinNativeCredit("proot", "Sandboxed rootfs execution", "GPL-2.0", "https://github.com/proot-me/proot"),
    )
