package com.winlator.cmod.feature.configs

import android.app.Activity
import android.content.Context
import android.provider.Settings
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.Player
import com.google.android.gms.tasks.Tasks
import com.winlator.cmod.feature.sync.google.PlayGamesBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Identity attached to a community-shared config.
 *
 * - `name` is shown publicly on the Best Configs board and is also the
 *   ownership key used (alongside [deviceId]) to permit deletion of an
 *   upload from any install that can prove it belongs to the same user.
 * - `deviceId` is a stable per-install identifier ([Settings.Secure.ANDROID_ID])
 *   used as the second ownership signal so a user who is not signed into
 *   Google Play Games can still delete their own uploads from the device
 *   they uploaded from.
 *
 * The name is intentionally NOT user-editable on upload — it is resolved
 * automatically from Google Play Games (or "Anonymous" when the user is
 * not signed in). This prevents abusive / inappropriate names on the
 * public community board.
 */
data class UploaderIdentity(
    val name: String,
    val deviceId: String,
) {
    companion object {
        const val ANONYMOUS_NAME = "Anonymous"

        /**
         * Resolve the uploader identity for the current device + signed-in user.
         *
         * Order of precedence for [name]:
         *  1. Google Play Games signed-in player display name
         *  2. "Anonymous" fallback when GPG is not signed in or the lookup fails
         *
         * Both branches always return a non-null, non-empty name so the upload
         * payload always carries an identity.
         */
        suspend fun resolve(activity: Activity): UploaderIdentity {
            val deviceId = readDeviceId(activity)
            val name = fetchPlayGamesDisplayName(activity) ?: ANONYMOUS_NAME
            return UploaderIdentity(name = name, deviceId = deviceId)
        }

        /**
         * Variant used when only an application [Context] is in hand — skips the
         * Play Games lookup (which requires an Activity) and always returns
         * "Anonymous". Use [resolve] from an Activity-scoped call site whenever
         * possible so signed-in users get credited.
         */
        fun resolveAnonymous(context: Context): UploaderIdentity =
            UploaderIdentity(name = ANONYMOUS_NAME, deviceId = readDeviceId(context))

        private fun readDeviceId(context: Context): String =
            runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull().orEmpty()

        /**
         * Returns the Play Games signed-in player's display name, or null if the
         * user is not signed in, the SDK call times out, or any error occurs.
         * Suspends on Dispatchers.IO so it can wait on the Tasks API without
         * blocking the caller's thread.
         */
        private suspend fun fetchPlayGamesDisplayName(activity: Activity): String? {
            if (activity.isFinishing || activity.isDestroyed) return null
            return withContext(Dispatchers.IO) {
                runCatching {
                    PlayGamesBootstrap.ensureInitialized(activity)
                    val authTask = PlayGames.getGamesSignInClient(activity).isAuthenticated
                    val auth = Tasks.await(authTask, 5, TimeUnit.SECONDS)
                    if (auth?.isAuthenticated != true) return@runCatching null
                    val playerTask = PlayGames.getPlayersClient(activity).currentPlayer
                    val player: Player = Tasks.await(playerTask, 5, TimeUnit.SECONDS)
                        ?: return@runCatching null
                    player.displayName?.takeIf { it.isNotBlank() }
                }.onFailure { Timber.tag(TAG).w(it, "GPG display-name lookup failed") }
                    .getOrNull()
            }
        }

        private const val TAG = "UploaderIdentity"
    }
}
