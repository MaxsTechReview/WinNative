package com.winlator.cmod.feature.sync.google

import android.content.Context
import com.google.android.gms.games.PlayGamesSdk
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Idempotent initializer for the Play Games v2 SDK.
 *
 * Called eagerly from `PluviaApp.onCreate()` so the SDK's silent re-auth bootstrap starts at
 * process start (covering the cold-start race where `isAuthenticated.await()` resolved false
 * before the background auth had landed). Also called defensively from every entry point that
 * needs PGS, so legacy callers and any new code paths are safe even if eager init is removed.
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
