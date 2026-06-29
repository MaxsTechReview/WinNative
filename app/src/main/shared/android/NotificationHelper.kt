package com.winlator.cmod.shared.android
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.R
import com.winlator.cmod.app.shell.UnifiedActivity
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import javax.inject.Inject
import javax.inject.Singleton

class NotificationHelper
    @Inject
    constructor(
        private val context: Context,
    ) {
        companion object {
            private const val CHANNEL_ID = "pluvia_foreground_service"
            private const val CHANNEL_NAME = "WinNative Foreground Service"
            private const val NOTIFICATION_ID = 1

            private const val CHAT_CHANNEL_ID = "winnative_steam_chat"
            private const val CHAT_CHANNEL_NAME = "Steam Chat"

            const val ACTION_EXIT = BuildConfig.APPLICATION_ID + ".EXIT"
            const val EXTRA_OPEN_CHAT_FRIEND_ID = BuildConfig.APPLICATION_ID + ".OPEN_CHAT_FRIEND_ID"
        }

        private val notificationManager: NotificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        init {
            createNotificationChannel()
        }

        private fun createNotificationChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Allows to display WinNative foreground notifications"
                    setShowBadge(false)
                }

            notificationManager.createNotificationChannel(channel)

            val chatChannel =
                NotificationChannel(
                    CHAT_CHANNEL_ID,
                    CHAT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Incoming Steam friend messages"
                    setShowBadge(true)
                }

            notificationManager.createNotificationChannel(chatChannel)
        }

        private fun chatNotificationId(friendId: Long): Int = 2_000_000 + ((friendId.hashCode() and 0x7FFFFFFF) % 1_000_000)

        fun notifyChatMessage(friendId: Long, sender: String, message: String) {
            val intent =
                Intent(context, UnifiedActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_CHAT_FRIEND_ID, friendId)
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    friendId.hashCode(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHAT_CHANNEL_ID)
                    .setContentTitle(sender)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(pendingIntent)
                    .build()
            notificationManager.notify(chatNotificationId(friendId), notification)
        }

        fun cancelChatNotification(friendId: Long) {
            notificationManager.cancel(chatNotificationId(friendId))
        }

        fun notify(content: String) {
            val notification = createForegroundNotification(content)
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        fun cancel() {
            notificationManager.cancel(NOTIFICATION_ID)
        }

        fun createForegroundNotification(content: String): Notification {
            val intent =
                Intent(context, UnifiedActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

            val stopIntent =
                Intent(context, SteamService::class.java).apply {
                    action = ACTION_EXIT
                }
            val stopPendingIntent =
                PendingIntent.getForegroundService(
                    context,
                    0,
                    stopIntent,
                    PendingIntent.FLAG_IMMUTABLE,
                )

            val smallIconRes = R.drawable.ic_notification

            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.common_ui_app_name))
                .setContentText(content)
                .setSmallIcon(smallIconRes)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(0, "Exit", stopPendingIntent)
                .build()
        }
    }
