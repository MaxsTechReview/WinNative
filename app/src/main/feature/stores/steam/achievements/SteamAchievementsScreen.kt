package com.winlator.cmod.feature.stores.steam.achievements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.winlator.cmod.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.statsgen.Achievement
import com.winlator.cmod.shared.ui.nav.DialogPaneNav
import com.winlator.cmod.shared.ui.nav.LocalPaneNav
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry
import com.winlator.cmod.shared.ui.nav.paneNavItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private val BgDark = Color(0xFF12121B)
private val SurfaceDark = Color(0xFF171722)
private val CardBorder = Color(0xFF2A2A3A)
private val Accent = Color(0xFF1A9FFF)
private val AccentGlow = Color(0xFF58A6FF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF93A6BC)
private val StatusOnline = Color(0xFF3FB950)
private val Scrim = Color(0xFF000000)

private fun iconUrl(appId: Int, icon: String?): String? {
    val raw = icon?.trim().orEmpty()
    if (raw.isEmpty() || raw.contains("steam_default_icon")) return null
    if (raw.startsWith("http")) return raw
    return "https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/$appId/$raw"
}

private fun Achievement.title(): String =
    displayName?.get("english") ?: displayName?.values?.firstOrNull() ?: name

private fun Achievement.desc(): String =
    description?.get("english") ?: description?.values?.firstOrNull() ?: ""

@Composable
fun SteamAchievementsScreen(
    appId: Int,
    appName: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    val registry = remember { PaneNavRegistry() }

    LaunchedEffect(appId) {
        loading = true
        achievements = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "achievements_$appId").apply { mkdirs() }
            SteamService.loadAchievements(appId, dir.absolutePath)
        }
        loading = false
    }

    val unlockedCount = achievements.count { it.unlocked == true }
    val total = achievements.size
    val ordered = achievements.sortedWith(
        compareByDescending<Achievement> { it.unlocked == true }
            .thenByDescending { it.unlockTimestamp ?: 0 },
    )

    CompositionLocalProvider(LocalPaneNav provides registry) {
    DialogPaneNav(registry, onDismiss = onClose)
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Scrim.copy(alpha = 0.6f))
                .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) {
        val dialogWidth = (maxWidth - 32.dp).coerceAtMost(560.dp)
        val dialogHeight = (maxHeight - 48.dp).coerceIn(360.dp, 640.dp)
        Surface(
            modifier =
                Modifier
                    .widthIn(min = 320.dp, max = dialogWidth)
                    .fillMaxWidth()
                    .height(dialogHeight),
            shape = RoundedCornerShape(14.dp),
            color = BgDark,
            border = BorderStroke(1.dp, CardBorder),
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                AchievementsHeader(appName = appName, unlocked = unlockedCount, total = total, onClose = onClose)
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { unlockedCount.toFloat() / total },
                        color = StatusOnline,
                        trackColor = SurfaceDark,
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        loading -> CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier.size(30.dp).align(Alignment.Center),
                        )
                        achievements.isEmpty() -> Text(
                            stringResource(R.string.steam_achievements_empty),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                        else -> Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ordered.forEach { ach -> AchievementRow(appId, ach) }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun AchievementsHeader(
    appName: String,
    unlocked: Int,
    total: Int,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.EmojiEvents,
                contentDescription = null,
                tint = AccentGlow,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(R.string.steam_achievements_title).uppercase(),
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
            )
            Text(
                appName,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (total > 0) {
            Surface(color = Accent.copy(alpha = 0.14f), shape = RoundedCornerShape(7.dp)) {
                Text(
                    "$unlocked / $total",
                    color = AccentGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp).paneNavItem(onActivate = onClose)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.steam_common_back),
                tint = TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AchievementRow(appId: Int, ach: Achievement) {
    val unlocked = ach.unlocked == true
    val hiddenLocked = ach.hidden == 1 && !unlocked
    val url = iconUrl(appId, if (unlocked) ach.icon ?: ach.iconGray else ach.iconGray ?: ach.icon)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (unlocked) Accent.copy(alpha = 0.06f) else SurfaceDark.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (unlocked) Accent.copy(alpha = 0.25f) else CardBorder),
        modifier = Modifier.fillMaxWidth().paneNavItem(cornerRadius = 12.dp, onActivate = {}),
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(BgDark),
                contentAlignment = Alignment.Center,
            ) {
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                        alpha = if (unlocked) 1f else 0.55f,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (hiddenLocked) stringResource(R.string.steam_achievements_hidden) else ach.title(),
                    color = if (unlocked) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = if (hiddenLocked) stringResource(R.string.steam_achievements_hidden_desc) else ach.desc()
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (unlocked) {
                    val ts = ach.unlockTimestamp ?: 0
                    val when_ = if (ts > 0) {
                        stringResource(R.string.steam_achievements_unlocked_on, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts.toLong() * 1000L)))
                    } else stringResource(R.string.steam_achievements_unlocked)
                    Text(
                        when_,
                        color = StatusOnline,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
