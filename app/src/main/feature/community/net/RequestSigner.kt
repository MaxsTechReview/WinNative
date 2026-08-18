package com.winlator.cmod.feature.community.net

import com.winlator.cmod.BuildConfig
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object RequestSigner {

    private val secret: ByteArray = BuildConfig.COMMUNITY_HMAC_SECRET.toByteArray()

    fun isConfigured(): Boolean = secret.isNotEmpty()

    fun headers(
        method: String,
        path: String,
        body: ByteArray,
        uploaderHandle: String,
        googleBacked: Boolean,
    ): Map<String, String> {
        if (!isConfigured()) {
            throw IOException("Community sharing is not available in this build")
        }
        val ts = (System.currentTimeMillis() / 1000L).toString()
        val nonce = UUID.randomUUID().toString()
        val bodyHash = sha256Hex(body)
        val msg = "$method\n$path\n$ts\n$nonce\n$bodyHash"
        return mapOf(
            "X-Wn-Timestamp" to ts,
            "X-Wn-Nonce" to nonce,
            "X-Wn-Signature" to hmacHex(msg),
            "X-Wn-Uploader" to uploaderHandle,
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
