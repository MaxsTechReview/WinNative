package com.winlator.cmod.feature.community

import android.app.Activity
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.games.PlayGames
import com.winlator.cmod.feature.sync.google.PlayGamesBootstrap
import java.security.MessageDigest
import java.util.UUID

/**
 * Provides the opaque uploader handle used for ownership + one-vote-per-user.
 *
 * The handle is a SHA-256 hash of either the Google Play Games player id (when
 * signed in) or a per-install random UUID stored in EncryptedSharedPreferences.
 * The raw player id / UUID is NEVER sent or displayed; only this hash is, and
 * the server further hashes it again for the public "WN-XXXX" badge. So the
 * displayed token reveals nothing and the handle itself is unguessable.
 */
object UploaderIdentity {

    @Volatile
    private var cachedGoogleId: String? = null

    @Volatile
    private var cachedDeviceUuid: String? = null

    @Volatile
    private var cachedDisplayName: String? = null

    /** Current opaque handle (hex). Prefers a resolved Google id, else device UUID. */
    fun handle(context: Context): String {
        val base = cachedGoogleId ?: deviceUuid(context)
        return sha256Hex("wn1:$base")
    }

    /** True if the handle is currently backed by a Google account. */
    fun isGoogleBacked(): Boolean = cachedGoogleId != null

    /**
     * The signed-in Google Play Games display name (gamer tag), or "" if not
     * Google-backed. Sent on upload so admins/mods can identify the uploader.
     * This is the Play Games alias, not the Google account email.
     */
    fun displayName(): String = cachedDisplayName ?: ""

    /**
     * Best-effort async resolution of the Google Play Games player id. Call when
     * the community feature is opened; the result is cached for subsequent
     * handle() calls. Falls back silently to the device UUID if unavailable.
     */
    fun resolveGoogle(activity: Activity, onDone: (Boolean) -> Unit = {}) {
        runCatching {
            PlayGamesBootstrap.ensureInitialized(activity)
            PlayGames.getPlayersClient(activity).currentPlayer
                .addOnSuccessListener { player ->
                    val id = player?.playerId
                    if (!id.isNullOrBlank()) {
                        cachedGoogleId = id
                        cachedDisplayName = player?.displayName
                        onDone(true)
                    } else onDone(false)
                }
                .addOnFailureListener { onDone(false) }
        }.onFailure { onDone(false) }
    }

    /**
     * Interactively prompt Google Play Games sign-in (if not already signed in),
     * then resolve the player id. Invokes [onDone] with true once a Google-backed
     * handle is available, false otherwise. Required before upload/vote.
     */
    fun signInAndResolve(activity: Activity, onDone: (Boolean) -> Unit) {
        if (isGoogleBacked()) {
            onDone(true)
            return
        }
        runCatching {
            PlayGamesBootstrap.ensureInitialized(activity)
            val client = PlayGames.getGamesSignInClient(activity)
            client.isAuthenticated.addOnCompleteListener { t ->
                if (t.isSuccessful && t.result?.isAuthenticated == true) {
                    resolveGoogle(activity, onDone)
                } else {
                    client.signIn().addOnCompleteListener { s ->
                        if (s.isSuccessful && s.result?.isAuthenticated == true) {
                            resolveGoogle(activity, onDone)
                        } else onDone(false)
                    }
                }
            }
        }.onFailure { onDone(false) }
    }

    private fun deviceUuid(context: Context): String {
        cachedDeviceUuid?.let { return it }
        synchronized(this) {
            cachedDeviceUuid?.let { return it }
            val prefs = securePrefs(context)
            var uuid = prefs.getString("uuid", null)
            if (uuid.isNullOrBlank()) {
                uuid = UUID.randomUUID().toString()
                prefs.edit().putString("uuid", uuid).apply()
            }
            cachedDeviceUuid = uuid
            return uuid
        }
    }

    private fun securePrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "community_identity_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Fall back to plain prefs (UUID is not sensitive on its own).
        context.applicationContext.getSharedPreferences("community_identity", Context.MODE_PRIVATE)
    }

    private fun sha256Hex(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}
