package com.winlator.cmod.feature.sync.google
import android.app.Activity
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import kotlinx.coroutines.launch

private val BgDark = Color(0xFF18181D)
private val CardDark = Color(0xFF1C1C2A)
private val CardBorder = Color(0xFF2A2A3A)
private val IconBoxBg = Color(0xFF242434)
private val Accent = Color(0xFF1A9FFF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)
private val StatusGreen = Color(0xFF3FB950)
private val WarningAmber = Color(0xFFFFC857)
private val DangerRed = Color(0xFFFF6B6B)
private val StoreLoginActionButtonWidth = 112.dp

@Composable
fun GoogleScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val navBarStartPadding = navBarPadding.calculateStartPadding(layoutDirection)
    val navBarEndPadding = navBarPadding.calculateEndPadding(layoutDirection)
    val navBarBottomPadding = navBarPadding.calculateBottomPadding()

    var googleSignedIn by remember { mutableStateOf(false) }
    var syncState by remember { mutableStateOf(CloudSyncManager.StoreLoginSyncState()) }
    var busy by remember { mutableStateOf(false) }

    val autoBackupPrefs =
        remember {
            context.getSharedPreferences("google_store_login_sync", Context.MODE_PRIVATE)
        }
    var autoBackupEnabled by remember {
        mutableStateOf(autoBackupPrefs.getBoolean("cloud_sync_auto_backup", false))
    }

    fun refreshState() {
        val currentActivity = activity ?: return
        scope.launch {
            syncState = CloudSyncManager.readStoreLoginState(currentActivity)
            googleSignedIn = syncState.googleSignedIn
        }
    }

    LaunchedEffect(activity) {
        val currentActivity = activity ?: return@LaunchedEffect
        busy = true
        syncState = CloudSyncManager.syncOnGoogleScreenOpened(currentActivity)
        googleSignedIn = syncState.googleSignedIn
        busy = false
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BgDark),
        contentPadding =
            PaddingValues(
                start = 16.dp + navBarStartPadding,
                top = 16.dp,
                end = 16.dp + navBarEndPadding,
                bottom = 16.dp + navBarBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("services_section") {
            SectionLabel(stringResource(R.string.google_cloud_services))
        }

        item("google_account_card") {
            GoogleAccountCard(
                isLoggedIn = googleSignedIn,
                busy = busy,
                onSignIn = {
                    val currentActivity = activity ?: return@GoogleAccountCard
                    busy = true
                    CloudSyncManager.signIn(currentActivity) { success, message ->
                        busy = false
                        googleSignedIn = success
                        AppUtils.showToast(context, message)
                        refreshState()
                    }
                },
                onSignOut = {
                    val currentActivity = activity ?: return@GoogleAccountCard
                    busy = true
                    CloudSyncManager.signOut(currentActivity) { success, message ->
                        busy = false
                        googleSignedIn = !success
                        AppUtils.showToast(context, message)
                        refreshState()
                    }
                },
            )
        }

        item("store_logins_section") {
            SectionLabel(stringResource(R.string.google_cloud_store_logins), modifier = Modifier.padding(top = 8.dp))
        }

        item("store_login_card") {
            StoreLoginCard(
                state = syncState,
                busy = busy,
                onBackup = {
                    val currentActivity = activity ?: return@StoreLoginCard
                    busy = true
                    scope.launch {
                        try {
                            val message = CloudSyncManager.backupStoreLogins(currentActivity)
                            AppUtils.showToast(context, message)
                            syncState = CloudSyncManager.readStoreLoginState(currentActivity)
                            googleSignedIn = syncState.googleSignedIn
                        } finally {
                            busy = false
                        }
                    }
                },
                onRestore = {
                    val currentActivity = activity ?: return@StoreLoginCard
                    busy = true
                    scope.launch {
                        try {
                            val message = CloudSyncManager.restoreStoreLogins(currentActivity)
                            AppUtils.showToast(context, message)
                            syncState = CloudSyncManager.readStoreLoginState(currentActivity)
                            googleSignedIn = syncState.googleSignedIn
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }

        item("auto_backup_section") {
            SectionLabel(stringResource(R.string.google_cloud_auto_backup), modifier = Modifier.padding(top = 8.dp))
        }

        item("auto_backup_card") {
            AutoBackupCard(
                enabled = autoBackupEnabled,
                googleSignedIn = googleSignedIn,
                busy = busy,
                onToggle = { newValue ->
                    if (newValue) {
                        val currentActivity = activity ?: return@AutoBackupCard
                        busy = true
                        scope.launch {
                            try {
                                val alreadyAuthorized = GameSaveBackupManager.requestDriveAuthorization(currentActivity)
                                if (alreadyAuthorized) {
                                    autoBackupEnabled = true
                                    autoBackupPrefs.edit().putBoolean("cloud_sync_auto_backup", true).apply()
                                } else {
                                    autoBackupEnabled = false
                                    autoBackupPrefs.edit().putBoolean("cloud_sync_auto_backup", false).apply()
                                }
                            } catch (e: Exception) {
                                AppUtils.showToast(context, "Drive authorization failed: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    } else {
                        autoBackupEnabled = false
                        autoBackupPrefs.edit().putBoolean("cloud_sync_auto_backup", false).apply()
                    }
                },
            )
        }
    }
}

@Composable
private fun AutoBackupCard(
    enabled: Boolean,
    googleSignedIn: Boolean,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .clickable(enabled = !busy) { onToggle(!enabled) },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(IconBoxBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudSync,
                    contentDescription = null,
                    tint = if (enabled && googleSignedIn) StatusGreen else TextSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }

            Spacer(Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.google_cloud_auto_backup),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.google_cloud_auto_backup_summary),
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.width(4.dp))

            Switch(
                checked = enabled && googleSignedIn,
                onCheckedChange = { onToggle(it) },
                enabled = !busy,
                modifier = Modifier.scale(0.78f),
                colors =
                    outlinedSwitchColors(
                        accentColor = StatusGreen,
                        textSecondaryColor = TextSecondary,
                    ),
            )
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = TextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun GoogleAccountCard(
    isLoggedIn: Boolean,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "pulse_google")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.7f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "scale_google",
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "alpha_google",
    )

    BoxWithConstraints {
        val compact = maxWidth < 400.dp

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
        ) {
            if (compact) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBox(icon = Icons.Outlined.Gamepad, tint = Color(0xFF34A853))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.google_cloud_play_games),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isLoggedIn) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(10.dp)
                                                    .scale(pulseScale)
                                                    .clip(CircleShape)
                                                    .background(StatusGreen.copy(alpha = pulseAlpha)),
                                        )
                                    }
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isLoggedIn) StatusGreen else TextSecondary.copy(alpha = 0.4f)),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text =
                                        when {
                                            busy -> stringResource(R.string.google_cloud_status_syncing)
                                            isLoggedIn -> stringResource(R.string.google_cloud_status_connected)
                                            else -> stringResource(R.string.google_cloud_status_not_signed_in)
                                        },
                                    color = if (isLoggedIn) StatusGreen else TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (!isLoggedIn) {
                            ActionButton(
                                label = if (busy) stringResource(R.string.google_cloud_working) else stringResource(R.string.google_cloud_sign_in),
                                textColor = Color(0xFF34A853),
                                enabled = !busy,
                                onClick = onSignIn,
                            )
                        } else {
                            ActionButton(
                                label = if (busy) stringResource(R.string.google_cloud_working) else stringResource(R.string.google_cloud_disable_sync),
                                textColor = DangerRed,
                                enabled = !busy,
                                onClick = onSignOut,
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBox(icon = Icons.Outlined.Gamepad, tint = Color(0xFF34A853))

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.google_cloud_play_games),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isLoggedIn) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(10.dp)
                                                .scale(pulseScale)
                                                .clip(CircleShape)
                                                .background(StatusGreen.copy(alpha = pulseAlpha)),
                                    )
                                }
                                Box(
                                    modifier =
                                        Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isLoggedIn) StatusGreen else TextSecondary.copy(alpha = 0.4f)),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text =
                                    when {
                                        busy -> stringResource(R.string.google_cloud_status_syncing)
                                        isLoggedIn -> stringResource(R.string.google_cloud_status_connected)
                                        else -> stringResource(R.string.google_cloud_status_not_signed_in)
                                    },
                                color = if (isLoggedIn) StatusGreen else TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    if (!isLoggedIn) {
                        ActionButton(
                            label = if (busy) stringResource(R.string.google_cloud_working) else stringResource(R.string.google_cloud_sign_in),
                            textColor = Color(0xFF34A853),
                            enabled = !busy,
                            onClick = onSignIn,
                        )
                    } else {
                        ActionButton(
                            label = if (busy) stringResource(R.string.google_cloud_working) else stringResource(R.string.google_cloud_disable_sync),
                            textColor = DangerRed,
                            enabled = !busy,
                            onClick = onSignOut,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreLoginCard(
    state: CloudSyncManager.StoreLoginSyncState,
    busy: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    val statusColor =
        when (state.status) {
            CloudSyncManager.SyncStatus.SYNCED -> StatusGreen
            CloudSyncManager.SyncStatus.RESTORE_AVAILABLE -> WarningAmber
            CloudSyncManager.SyncStatus.BACKUP_PENDING -> WarningAmber
            CloudSyncManager.SyncStatus.ERROR -> DangerRed
            CloudSyncManager.SyncStatus.NOT_SIGNED_IN -> TextSecondary
            CloudSyncManager.SyncStatus.EMPTY -> TextSecondary
        }
    val stores =
        remember(state.localStores, state.cloudStores) {
            state.localStores
                .union(state.cloudStores)
                .toList()
                .sorted()
        }
    val lastSyncLabel =
        state.lastSyncTime?.let {
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
    ) {
        val compact = maxWidth < 400.dp

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIconBox(statusColor = statusColor)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.google_cloud_store_logins),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (stores.isNotEmpty()) {
                            Spacer(Modifier.width(10.dp))
                            StoreBadgeRow(
                                stores = stores,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (busy) stringResource(R.string.google_cloud_store_login_sync_busy) else state.detail,
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            if (compact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text =
                                when {
                                    state.status == CloudSyncManager.SyncStatus.ERROR -> {
                                        stringResource(R.string.google_cloud_saved_games_unavailable)
                                    }

                                    state.cloudStores.isNotEmpty() -> {
                                        stringResource(R.string.google_cloud_snapshot_ready)
                                    }

                                    state.localStores.isNotEmpty() -> {
                                        stringResource(R.string.google_cloud_waiting_first_backup)
                                    }

                                    else -> {
                                        stringResource(R.string.google_cloud_no_store_logins_detected)
                                    }
                                },
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (lastSyncLabel != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.google_cloud_last_synced, lastSyncLabel),
                                color = TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionButton(
                            label = if (busy) stringResource(R.string.google_cloud_working) else stringResource(R.string.google_cloud_backup),
                            textColor = WarningAmber,
                            icon = Icons.Outlined.Upload,
                            enabled = !busy && state.googleSignedIn && state.localStores.isNotEmpty(),
                            modifier = Modifier.width(StoreLoginActionButtonWidth),
                            onClick = onBackup,
                        )
                        Spacer(Modifier.width(8.dp))
                        ActionButton(
                            label = if (busy) stringResource(R.string.google_cloud_working) else stringResource(R.string.google_cloud_restore),
                            textColor = Accent,
                            icon = Icons.Outlined.Restore,
                            enabled = !busy && state.googleSignedIn && state.cloudStores.isNotEmpty(),
                            modifier = Modifier.width(StoreLoginActionButtonWidth),
                            onClick = onRestore,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                when {
                                    state.status == CloudSyncManager.SyncStatus.ERROR -> {
                                        stringResource(R.string.google_cloud_saved_games_unavailable)
                                    }

                                    state.cloudStores.isNotEmpty() -> {
                                        stringResource(R.string.google_cloud_snapshot_ready)
                                    }

                                    state.localStores.isNotEmpty() -> {
                                        stringResource(R.string.google_cloud_waiting_first_backup)
                                    }

                                    else -> {
                                        stringResource(R.string.google_cloud_no_store_logins_detected)
                                    }
                                },
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (lastSyncLabel != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.google_cloud_last_synced, lastSyncLabel),
                                color = TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(
                            label = if (busy) stringResource(R.string.google_cloud_working) else stringResource(R.string.google_cloud_backup),
                            textColor = WarningAmber,
                            icon = Icons.Outlined.Upload,
                            enabled = !busy && state.googleSignedIn && state.localStores.isNotEmpty(),
                            modifier = Modifier.width(StoreLoginActionButtonWidth),
                            onClick = onBackup,
                        )
                        ActionButton(
                            label = if (busy) stringResource(R.string.google_cloud_working) else stringResource(R.string.google_cloud_restore),
                            textColor = Accent,
                            icon = Icons.Outlined.Restore,
                            enabled = !busy && state.googleSignedIn && state.cloudStores.isNotEmpty(),
                            modifier = Modifier.width(StoreLoginActionButtonWidth),
                            onClick = onRestore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreBadgeRow(
    stores: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stores.forEach { store ->
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(IconBoxBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = store,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun IconBox(
    icon: ImageVector,
    tint: Color,
) {
    Box(
        modifier =
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(IconBoxBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun StatusIconBox(statusColor: Color) {
    Box(
        modifier = Modifier.size(38.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(11.dp))
                    .background(IconBoxBg),
        )
        Icon(
            imageVector = Icons.Outlined.CloudSync,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(21.dp),
        )
        if (statusColor == DangerRed || statusColor == TextSecondary) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(DangerRed)
                        .border(1.dp, CardDark, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    textColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "btnScale",
    )

    Box(
        modifier =
            modifier
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222232))
                .border(
                    1.dp,
                    if (enabled) textColor.copy(alpha = 0.30f) else TextSecondary.copy(alpha = 0.2f),
                    RoundedCornerShape(8.dp),
                ).pointerInput(onClick, enabled) {
                    detectTapGestures(
                        onPress = {
                            if (!enabled) return@detectTapGestures
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = {
                            if (enabled) {
                                onClick()
                            }
                        },
                    )
                }.padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) textColor else TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = if (enabled) textColor else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
