// IMPORTANT: the package path below is hard-bound to JNI symbol names in
// app/src/main/cpp/wn-steam-client/jni/wn_steam_jni.cpp
// (Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnConnection_*).
// Do NOT move this file or rename the package without updating that file.
package com.winlator.cmod.feature.stores.steam.wnsteam

import java.util.concurrent.atomic.AtomicLong

/**
 * JVM-side handle to a native EncryptedChannel that runs the Steam CM
 * handshake and AES-CBC+HMAC envelope on top of an IXWebSocket-backed
 * WebSocket transport.
 *
 * Lifecycle:
 *   1. Construct (allocates a native handle).
 *   2. Optionally call [setCaBundlePath] to point at a Mozilla CA PEM bundle.
 *   3. Call [setObserver] before [connect] so handshake callbacks aren't lost.
 *   4. Call [connect] with a `wss://...:443/cmsocket/` URL.
 *   5. Wait for [WnConnectionObserver.onConnected].
 *   6. [send] / [disconnect] freely.
 *   7. [close] when done — releases the native handle. Safe to call multiple
 *      times. Must be called or the native allocation leaks.
 *
 * Thread-safety: all public methods are thread-safe. [close] is idempotent.
 */
class WnConnection : AutoCloseable {

    private val nativeHandle: AtomicLong

    init {
        WnSteamClient.ensureLoaded()
        val h = nativeCreate()
        require(h != 0L) { "wnsteam: nativeCreate returned 0" }
        nativeHandle = AtomicLong(h)
    }

    /**
     * Sets the path to a single-file PEM CA bundle (Mozilla cacert.pem or
     * equivalent) that the underlying TLS stack uses to verify Steam's
     * certificate. Without a valid bundle, the WSS handshake will fail on
     * Android. Must be called before [connect].
     */
    fun setCaBundlePath(path: String) {
        val h = nativeHandle.get()
        if (h == 0L) return
        nativeSetCaBundlePath(h, path)
    }

    /**
     * Installs / replaces the lifecycle observer. Pass null to clear.
     */
    fun setObserver(observer: WnConnectionObserver?) {
        val h = nativeHandle.get()
        if (h == 0L) return
        nativeSetObserver(h, observer)
    }

    /**
     * Begins connecting to `url` (e.g.
     * "wss://cm1-fra1.cm.steampowered.com:443/cmsocket/").
     * Returns false if a connection is already in progress or open.
     */
    fun connect(url: String): Boolean {
        val h = nativeHandle.get()
        if (h == 0L) return false
        return nativeConnect(h, url)
    }

    /** Initiates an orderly close. The disconnect callback fires after. */
    fun disconnect() {
        val h = nativeHandle.get()
        if (h == 0L) return
        nativeDisconnect(h)
    }

    /**
     * Sends one application-layer message over the encrypted channel.
     * Returns false if the channel is not yet in the Encrypted state.
     */
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
        // Safety net only — callers MUST `close()` to release the native
        // allocation deterministically. Finalizers are unreliable on ART.
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
