// IMPORTANT: the package path below is hard-bound to the JNI lookup in
// app/src/main/cpp/wn-steam-client/jni/wn_steam_jni.cpp (the constant
// kObserverClassName). Do NOT move this file or rename the package
// without updating that constant.
package com.winlator.cmod.feature.stores.steam.wnsteam

/**
 * Callback surface for [WnConnection]. Implementations receive lifecycle and
 * data events from the native EncryptedChannel.
 *
 * Threading: methods fire on the native transport's worker thread. Marshal
 * to your own dispatcher before touching UI state. Methods must not throw —
 * the native side catches exceptions but they produce log spam and lose the
 * stack.
 *
 * `onDisconnected.reason` is the ordinal of a native ChannelDisconnectReason:
 *   0 = UserInitiated
 *   1 = TransportError
 *   2 = HandshakeProtocolError
 *   3 = HandshakeFailed
 *   4 = EnvelopeDecryptFailed
 *   5 = HmacMismatch
 */
interface WnConnectionObserver {
    /** Encrypted channel reached the Encrypted state; safe to send messages. */
    fun onConnected()

    /** Channel terminated. See [reason] ordinal mapping above. */
    fun onDisconnected(reason: Int, detail: String)

    /**
     * One decrypted application-layer message. Bytes start with either
     * a CMsgProtoBufHeader+protobuf (proto flag set on EMsg) or a
     * MsgHdr/ExtendedClientMsgHdr+struct body, exactly as Steam's CM
     * dispatches them. The Kotlin layer parses based on the EMsg flag.
     */
    fun onMessage(bytes: ByteArray)
}
