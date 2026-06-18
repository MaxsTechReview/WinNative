package com.winlator.cmod.feature.community.net

import com.winlator.cmod.BuildConfig
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Produces the HMAC authentication headers for every API request. The signature
 * is computed with a secret embedded in the signed release APK over:
 *   METHOD \n PATH \n TIMESTAMP \n NONCE \n SHA256(body)
 * matching the server's verification in auth.py.
 */
object RequestSigner {

    private val secret: ByteArray = BuildConfig.COMMUNITY_HMAC_SECRET.toByteArray()

    fun headers(
        method: String,
        path: String,
        body: ByteArray,
        uploaderHandle: String,
        googleBacked: Boolean,
    ): Map<String, String> {
        val ts = (System.currentTimeMillis() / 1000L).toString()
        val nonce = UUID.randomUUID().toString()
        val bodyHash = sha256Hex(body)
        val msg = "$method\n$path\n$ts\n$nonce\n$bodyHash"
        return mapOf(
            "X-Wn-Timestamp" to ts,
            "X-Wn-Nonce" to nonce,
            "X-Wn-Signature" to hmacHex(msg),
            "X-Wn-Uploader" to uploaderHandle,
            // Identity class: gates upload/vote and ties bans to a Google account.
            "X-Wn-Auth" to (if (googleBacked) "google" else "device"),
        )
    }

    private fun hmacHex(msg: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(msg.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
