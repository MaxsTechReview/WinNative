// JNI symbols depend on this package path and class name.
package com.winlator.cmod.feature.stores.steam.wnsteam

import java.util.concurrent.atomic.AtomicLong

// Thread-safe JVM handle for the native Steam CM encrypted channel.
class WnConnection : AutoCloseable {

    private val nativeHandle: AtomicLong

    init {
        WnSteamClient.ensureLoaded()
        val h = nativeCreate()
        require(h != 0L) { "wnsteam: nativeCreate returned 0" }
        nativeHandle = AtomicLong(h)
    }

    // Set the PEM CA bundle used by TLS before [connect].
    fun setCaBundlePath(path: String) {
        val h = nativeHandle.get()
        if (h == 0L) return
        nativeSetCaBundlePath(h, path)
    }

    // Install or clear the lifecycle observer.
    fun setObserver(observer: WnConnectionObserver?) {
        val h = nativeHandle.get()
        if (h == 0L) return
        nativeSetObserver(h, observer)
    }

    // Connect to a Steam CM WSS URL.
    fun connect(url: String): Boolean {
        val h = nativeHandle.get()
        if (h == 0L) return false
        return nativeConnect(h, url)
    }

    fun disconnect() {
        val h = nativeHandle.get()
        if (h == 0L) return
        nativeDisconnect(h)
    }

    // Send one application-layer message over the encrypted channel.
    fun send(bytes: ByteArray): Boolean {
        val h = nativeHandle.get()
        if (h == 0L) return false
        return nativeSend(h, bytes)
    }

    override fun close() {
        val h = nativeHandle.getAndSet(0L)
        if (h != 0L) nativeDestroy(h)
    }

    @Suppress("ProtectedInFinal", "unused")
    protected fun finalize() {
        // Safety net only; callers should close deterministically.
        close()
    }

    companion object {
        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeSetCaBundlePath(handle: Long, path: String)
        @JvmStatic private external fun nativeSetObserver(handle: Long, observer: WnConnectionObserver?)
        @JvmStatic private external fun nativeConnect(handle: Long, url: String): Boolean
        @JvmStatic private external fun nativeDisconnect(handle: Long)
        @JvmStatic private external fun nativeSend(handle: Long, data: ByteArray): Boolean
    }
}
