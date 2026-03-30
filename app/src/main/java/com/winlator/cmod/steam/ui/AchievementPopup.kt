package com.winlator.cmod.steam.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

data class AchievementNotification(
    val name: String,
    val displayName: String,
    val iconPath: String? = null
)

/** Java-friendly state holder for achievement notifications. */
class AchievementPopupState {
    internal var notification by mutableStateOf<AchievementNotification?>(null)

    fun show(notification: AchievementNotification) {
        this.notification = notification
    }

    fun dismiss() {
        notification = null
    }
}

fun setupAchievementPopup(
    composeView: ComposeView,
    state: AchievementPopupState
) {
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    composeView.setContent {
        AchievementPopup(
            notification = state.notification,
            onDismissed = { state.dismiss() }
        )
    }
}

@Composable
fun AchievementPopup(
    notification: AchievementNotification?,
    onDismissed: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(notification) {
        if (notification != null) {
            visible = true
            delay(4000)
            visible = false
            delay(500)
            onDismissed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(400)
            ) + fadeIn(animationSpec = tween(400)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(400)
            ) + fadeOut(animationSpec = tween(400))
        ) {
            val shape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .shadow(
                        elevation = 16.dp,
                        shape = shape,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xF5141414), Color(0xF50A0A0A))
                        )
                    )
                    .border(1.dp, Color(0x33FFFFFF), shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .widthIn(max = 340.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val iconBitmap = remember(notification?.iconPath) {
                    notification?.iconPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
                        } else null
                    }
                }

                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "ACHIEVEMENT UNLOCKED",
                        color = Color(0xFFFFD400),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = notification?.displayName ?: "",
                        color = Color(0xFFE0E0E0),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
