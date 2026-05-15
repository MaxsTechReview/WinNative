// IMPORTANT: the package path below is hard-bound to JNI symbol names in
// app/src/main/cpp/wn-steam-client/jni/wn_session_jni.cpp
// (Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_*).
// Do NOT move this file or rename the package without updating that file.
package com.winlator.cmod.feature.stores.steam.wnsteam

import java.util.concurrent.atomic.AtomicLong

/**
 * Production-facing handle to a native CMClient + AuthSession pair.
 * Replaces JavaSteam's `SteamClient + SteamAuthentication` surface for
 * the login flow in Phase 2.
 *
 * Lifecycle:
 *   1. Construct (allocates a native handle).
 *   2. [setCaBundlePath] (required for TLS).
 *   3. [setStateObserver] before [connect].
 *   4. [connect] with a `wss://...:443/cmsocket/` URL.
 *   5. Wait for [WnSteamStateObserver.onStateChanged] reporting Connected (=2).
 *   6. [startLoginWithCredentials] or [startLoginWithQr], get a refresh token.
 *   7. [logonWithRefreshToken] — moves to LoggedOn (state 3).
 *   8. [close] when done.
 */
class WnSteamSession : AutoCloseable {

    private val nativeHandle: AtomicLong

    init {
        WnSteamClient.ensureLoaded()
        val h = nativeCreate()
        require(h != 0L) { "wnsteam: nativeCreate returned 0" }
        nativeHandle = AtomicLong(h)
    }

    fun setCaBundlePath(path: String) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSetCaBundlePath(h, path)
    }

    fun setStateObserver(observer: WnSteamStateObserver?) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSetStateObserver(h, observer)
    }

    fun connect(url: String): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeConnect(h, url)
    }

    fun disconnect() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeDisconnect(h)
    }

    /**
     * Begins the credentials login flow:
     *   GetPasswordRSAPublicKey → encrypt password → BeginAuthSession →
     *   prompt the [authenticator] for Steam Guard codes as needed →
     *   PollAuthSessionStatus until a refresh token is issued.
     *
     * Calls [callback] with the final [WnAuthResult] on a native worker
     * thread. Marshal to your own dispatcher before touching UI state.
     */
    fun startLoginWithCredentials(
        username: String,
        password: String,
        persistentSession: Boolean,
        authenticator: WnAuthenticator,
        callback: WnAuthCallback,
    ) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeStartLoginWithCredentials(h, username, password, persistentSession,
            authenticator, callback)
    }

    fun startLoginWithQr(
        qrCallback: WnQrCallback,
        resultCallback: WnAuthCallback,
    ) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeStartLoginWithQr(h, qrCallback, resultCallback)
    }

    /** Cancel any in-flight credentials or QR session. */
    fun cancelLogin() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeCancelLogin(h)
    }

    /**
     * Sends CMsgClientLogon with the given refresh token (the access_token
     * field on the wire — confusingly named). After this, the channel
     * transitions to LoggedOn and the heartbeat starts automatically.
     *
     * @param accountName Steam login username. REQUIRED — Steam rejects with
     * EResult.InvalidPassword if omitted, even when the refresh token is valid.
     * @param steamId optional client-supplied SteamID64 (0 = let CM resolve).
     * Returns false if the channel isn't yet in Connected state.
     */
    fun logonWithRefreshToken(refreshToken: String, accountName: String, steamId: Long = 0L): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeLogonWithRefreshToken(h, refreshToken, accountName, steamId)
    }

    /** Current native ClientState (0..3). */
    fun state(): Int {
        val h = nativeHandle.get(); if (h == 0L) return 0
        return nativeState(h)
    }

    /** SteamID64 after successful logon, or 0 if not logged on. */
    fun steamId(): Long {
        val h = nativeHandle.get(); if (h == 0L) return 0L
        return nativeSteamId(h)
    }

    override fun close() {
        val h = nativeHandle.getAndSet(0L)
        if (h != 0L) nativeDestroy(h)
    }

    @Suppress("ProtectedInFinal", "unused")
    protected fun finalize() { close() }

    companion object {
        /**
         * Synchronously resolves a WSS URL for a Steam CM. Uses Steam
         * Directory (`GetCMListForConnect`) with a hardcoded SteamKit-style
         * fallback list if the directory is unreachable. Caller must be on
         * a background dispatcher — this blocks on a curl HTTPS call.
         *
         * @param caBundlePath absolute path to a single-file PEM trust
         * bundle (typically from CaBundleExtractor.ensureBundle). Empty
         * string disables TLS verification source — the call will then
         * fail because verifypeer is on.
         *
         * @return WSS URL or empty string on total failure.
         */
        fun pickCmUrl(caBundlePath: String): String {
            WnSteamClient.ensureLoaded()
            return nativePickCmUrl(caBundlePath)
        }

        @JvmStatic private external fun nativePickCmUrl(caBundlePath: String): String

        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeSetCaBundlePath(handle: Long, path: String)
        @JvmStatic private external fun nativeSetStateObserver(handle: Long, observer: WnSteamStateObserver?)
        @JvmStatic private external fun nativeConnect(handle: Long, url: String): Boolean
        @JvmStatic private external fun nativeDisconnect(handle: Long)
        @JvmStatic private external fun nativeStartLoginWithCredentials(
            handle: Long,
            username: String,
            password: String,
            persistentSession: Boolean,
            authenticator: WnAuthenticator,
            callback: WnAuthCallback,
        )
        @JvmStatic private external fun nativeStartLoginWithQr(
            handle: Long,
            qrCallback: WnQrCallback,
            resultCallback: WnAuthCallback,
        )
        @JvmStatic private external fun nativeCancelLogin(handle: Long)
        @JvmStatic private external fun nativeLogonWithRefreshToken(
            handle: Long,
            refreshToken: String,
            accountName: String,
            steamId: Long,
        ): Boolean
        @JvmStatic private external fun nativeState(handle: Long): Int
        @JvmStatic private external fun nativeSteamId(handle: Long): Long
    }
}
