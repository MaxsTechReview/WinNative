package com.winlator.cmod.feature.sync.google

import android.content.Context
import com.google.android.gms.games.PlayGamesSdk
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lazy initializer for the Play Games v2 SDK. Called from every entry point that needs it
 * (sign-in, snapshot client, auth check) instead of eagerly from [PluviaApp.onCreate] —
 * keeps cold-start fast and avoids initializing Google libs for users who never touch cloud sync.
 */
object PlayGamesBootstrap {
    private const val TAG = "PlayGamesBootstrap"
    private val initialized = AtomicBoolean(false)

    @JvmStatic
    fun ensureInitialized(context: Context) {
        if (initialized.get()) return

        synchronized(this) {
            if (initialized.get()) return

            PlayGamesSdk.initialize(context.applicationContext)
            initialized.set(true)
            Timber.tag(TAG).i("Initialized Play Games SDK")
        }
    }
}
