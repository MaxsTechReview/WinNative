package com.winlator.cmod.feature.community

import android.app.Activity
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.games.PlayGames
import com.winlator.cmod.feature.sync.google.PlayGamesBootstrap
import java.security.MessageDigest
import java.util.UUID

object UploaderIdentity {

    @Volatile
    private var cachedGoogleId: String? = null

    @Volatile
    private var cachedDeviceUuid: String? = null

    @Volatile
    private var cachedDisplayName: String? = null

    fun handle(context: Context): String {
        val base = cachedGoogleId ?: deviceUuid(context)
        return sha256Hex("wn1:$base")
    }

    fun isGoogleBacked(): Boolean = cachedGoogleId != null

    fun displayName(): String = cachedDisplayName ?: ""

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
        context.applicationContext.getSharedPreferences("community_identity", Context.MODE_PRIVATE)
    }

    private fun sha256Hex(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}
