package com.winlator.cmod.feature.sync.google

import android.content.Context
import com.google.android.gms.games.PlayGamesSdk
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

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
