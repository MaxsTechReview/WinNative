package com.winlator.cmod.feature.stores.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide bus for [StoreSessionEvent]s. Stores emit events here when they detect
 * expired credentials or successful background refreshes; the shell observes this flow
 * and surfaces them in the UI (toasts, banners, etc.).
 */
object StoreSessionBus {
    private val _events =
        MutableSharedFlow<StoreSessionEvent>(
            replay = 0,
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events = _events.asSharedFlow()

    fun emit(event: StoreSessionEvent) {
        _events.tryEmit(event)
    }
}
