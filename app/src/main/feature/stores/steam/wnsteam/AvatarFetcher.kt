package com.winlator.cmod.feature.stores.steam.wnsteam

import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object AvatarFetcher {
    private const val TAG = "AvatarFetcher"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS    = 12_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Job>()

    fun enqueueAllTiers(steamId: Long, hashHex: String) {
        enqueue(steamId, hashHex, tier = 0)
        enqueue(steamId, hashHex, tier = 1)
        enqueue(steamId, hashHex, tier = 2)
    }

    fun enqueue(steamId: Long, hashHex: String, tier: Int = 0) {
        if (steamId == 0L || tier !in 0..2) return
        if (hashHex.isEmpty() || hashHex.length % 2 != 0) return
        if (!hashHex.all { it in '0'..'9' || it in 'a'..'f' }) return
        val key = "$hashHex:$tier"
        if (inFlight.containsKey(key)) return
        val job = scope.launch {
            try {
                val img = fetchAndDecode(hashHex, tier)
                if (img == null) {
                    Timber.tag(TAG).w("fetchAndDecode returned null hash=$hashHex tier=$tier")
                    return@launch
                }
                val handle = WnLibSteamClient.nativePushFriendAvatar(
                    steamId, tier, img.width, img.height, img.rgba)
                Timber.tag(TAG).i(
                    "pushed avatar sid=%d tier=%d %dx%d → handle=%d",
                    steamId, tier, img.width, img.height, handle)
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "fetch failed sid=$steamId hash=$hashHex tier=$tier")
            } finally {
                inFlight.remove(key)
            }
        }
        inFlight[key] = job
    }

    @Throws(Exception::class)
    fun fetchAndDecode(hashHex: String, tier: Int): RgbaImage? {
        val suffix = when (tier) {
            0 -> ""
            1 -> "_medium"
            2 -> "_full"
            else -> return null
        }
        val url = URL("https://avatars.akamai.steamstatic.com/${hashHex}${suffix}.jpg")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout    = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod  = "GET"
        }
        val bytes = try {
            if (conn.responseCode !in 200..299) {
                Timber.tag(TAG).w("HTTP %d for %s", conn.responseCode, url)
                return null
            }
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0 || w > 1024 || h > 1024) {
            Timber.tag(TAG).w("rejecting bitmap %dx%d (out of bounds)", w, h)
            return null
        }
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        bmp.recycle()
        val rgba = ByteArray(w * h * 4)
        for (i in 0 until w * h) {
            val px = argb[i]
            rgba[i * 4 + 0] = ((px shr 16) and 0xFF).toByte() // R
            rgba[i * 4 + 1] = ((px shr 8)  and 0xFF).toByte() // G
            rgba[i * 4 + 2] = ( px         and 0xFF).toByte() // B
            rgba[i * 4 + 3] = ((px ushr 24) and 0xFF).toByte() // A
        }
        return RgbaImage(w, h, rgba)
    }

    data class RgbaImage(val width: Int, val height: Int, val rgba: ByteArray)
}
