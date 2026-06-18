package com.winlator.cmod.feature.community.net

import android.content.Context
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.feature.community.DeviceIdentity
import com.winlator.cmod.feature.community.UploaderIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OkHttp client for the community config API. Every request carries the HMAC
 * headers from [RequestSigner]. All calls are suspending and run on the IO
 * dispatcher. Non-2xx responses throw [IOException] with the server's detail.
 */
class CommunityApiClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val base = BuildConfig.COMMUNITY_API_BASE.toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }
    private val JSON_MEDIA = "application/json".toMediaType()

    suspend fun listConfigs(
        gameKey: String,
        filter: CommunityFilter,
        hw: DeviceIdentity.HardwareBlock,
    ): ListResponse = withContext(Dispatchers.IO) {
        val url = base.newBuilder()
            .addPathSegment("configs")
            .addQueryParameter("gameKey", gameKey)
            .addQueryParameter("filter", filter.wire)
            .addQueryParameter("soc", hw.socModel.ifBlank { hw.boardPlatform })
            .addQueryParameter("brand", hw.brand)
            .addQueryParameter("model", hw.modelNumber)
            .addQueryParameter("codename", hw.deviceCodename)
            .build()
        json.decodeFromString(ListResponse.serializer(), exec("GET", url, null))
    }

    suspend fun fetchSettings(id: String): JSONObject = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegment("configs").addPathSegment(id).build()
        val obj = JSONObject(exec("GET", url, null))
        obj.optJSONObject("settings") ?: JSONObject()
    }

    suspend fun upload(
        gameKey: String,
        store: String,
        settings: JSONObject,
        hw: DeviceIdentity.HardwareBlock,
    ): UploadResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("schemaVersion", 1)
            .put("gameKey", gameKey)
            .put("store", store)
            // Play Games display name, shown only in admin/mod views for moderation.
            .put("uploaderName", UploaderIdentity.displayName())
            .put("settings", settings)
            .put("hardware", JSONObject()
                .put("socModel", hw.socModel)
                .put("socManufacturer", hw.socManufacturer)
                .put("boardPlatform", hw.boardPlatform)
                .put("deviceCodename", hw.deviceCodename)
                .put("modelNumber", hw.modelNumber)
                .put("modelRegion", hw.modelRegion)
                .put("brand", hw.brand)
                .put("marketName", hw.marketName))
        val url = base.newBuilder().addPathSegment("configs").build()
        json.decodeFromString(UploadResult.serializer(),
            exec("POST", url, payload.toString().toByteArray()))
    }

    suspend fun deleteConfig(id: String): Boolean = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegment("configs").addPathSegment(id).build()
        exec("DELETE", url, ByteArray(0)); true
    }

    suspend fun vote(id: String, up: Boolean): VoteResult = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegment("configs").addPathSegment(id)
            .addPathSegment("vote").build()
        val body = JSONObject().put("value", if (up) 1 else -1).toString().toByteArray()
        json.decodeFromString(VoteResult.serializer(), exec("POST", url, body))
    }

    suspend fun report(id: String, reason: String): Boolean = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegment("configs").addPathSegment(id)
            .addPathSegment("report").build()
        val body = JSONObject().put("reason", reason).toString().toByteArray()
        exec("POST", url, body); true
    }

    private fun exec(method: String, url: okhttp3.HttpUrl, body: ByteArray?): String {
        val bodyBytes = body ?: ByteArray(0)
        // Resolve identity per-request so it reflects a Google sign-in that may
        // have completed after this client was constructed.
        val handle = UploaderIdentity.handle(context)
        val googleBacked = UploaderIdentity.isGoogleBacked()
        val headers = RequestSigner.headers(
            method, url.encodedPath, bodyBytes, handle, googleBacked)
        val builder = Request.Builder().url(url)
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete(bodyBytes.toRequestBody(JSON_MEDIA))
            else -> builder.method(method, bodyBytes.toRequestBody(JSON_MEDIA))
        }
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrDefault("")
                throw IOException(if (detail.isNotBlank()) detail else "HTTP ${resp.code}")
            }
            return text
        }
    }
}
