package com.winlator.cmod.feature.stores.steam.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.statsgen.Achievement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private val BgDark = Color(0xFF18181D)
private val SurfaceDark = Color(0xFF1E252E)
private val CardBorder = Color(0xFF2A2A3A)
private val Accent = Color(0xFF1A9FFF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)
private val StatusOnline = Color(0xFF3FB950)

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

    Surface(color = BgDark, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.fillMaxWidth().background(SurfaceDark).padding(bottom = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.steam_common_back), tint = TextPrimary)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            appName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.steam_achievements_title),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (total > 0) {
                        Text(
                            "$unlockedCount / $total",
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(end = 14.dp),
                        )
                    }
                }
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { unlockedCount.toFloat() / total },
                        color = StatusOnline,
                        trackColor = BgDark,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
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
                    else -> LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    ) {
                        items(ordered) { ach -> AchievementRow(appId, ach) }
                    }
                }
            }
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
        modifier = Modifier.fillMaxWidth(),
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
