package com.winlator.cmod.feature.community.net

import android.content.Context
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.feature.community.CommunitySettings
import com.winlator.cmod.feature.community.DeviceIdentity
import com.winlator.cmod.feature.community.UploaderIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CommunityApiClient(private val context: Context) {

    companion object {
        private const val MAX_RESPONSE_BYTES = 1L shl 20

        private val JSON_MEDIA = "application/json".toMediaType()

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        private val json = Json { ignoreUnknownKeys = true }

        private val base: HttpUrl? by lazy {
            BuildConfig.COMMUNITY_API_BASE.toHttpUrlOrNull()
                ?.takeIf { it.isHttps || BuildConfig.DEBUG }
        }

        fun isConfigured(): Boolean =
            base != null && RequestSigner.isConfigured()
    }

    private fun requireBase(): HttpUrl =
        base ?: throw IOException("Community sharing is not available in this build")

    suspend fun listConfigs(
        gameKey: String,
        filter: CommunityFilter,
        hw: DeviceIdentity.HardwareBlock,
    ): ListResponse = withContext(Dispatchers.IO) {
        val url = requireBase().newBuilder()
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
        val url = requireBase().newBuilder()
            .addPathSegment("configs").addPathSegment(id).build()
        val obj = JSONObject(exec("GET", url, null))
        val settings = obj.optJSONObject("settings") ?: JSONObject()
        val safe = JSONObject()
        val keys = settings.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = settings.optString(key, "")
            if (CommunitySettings.accepts(key, value)) safe.put(key, value)
        }
        safe
    }

    suspend fun upload(
        gameKey: String,
        store: String,
        settings: JSONObject,
        hw: DeviceIdentity.HardwareBlock,
    ): UploadResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("schemaVersion", CommunitySettings.SCHEMA_VERSION)
            .put("gameKey", gameKey)
            .put("store", store)
            .put("uploaderName", UploaderIdentity.displayName())
            .put("settings", settings)
            .put(
                "hardware",
                JSONObject()
                    .put("socModel", hw.socModel)
                    .put("socManufacturer", hw.socManufacturer)
                    .put("boardPlatform", hw.boardPlatform)
                    .put("deviceCodename", hw.deviceCodename)
                    .put("modelNumber", hw.modelNumber)
                    .put("modelRegion", hw.modelRegion)
                    .put("brand", hw.brand)
                    .put("marketName", hw.marketName),
            )
        val url = requireBase().newBuilder().addPathSegment("configs").build()
        json.decodeFromString(
            UploadResult.serializer(),
            exec("POST", url, payload.toString().toByteArray()),
        )
    }

    suspend fun deleteConfig(id: String): Boolean = withContext(Dispatchers.IO) {
        val url = requireBase().newBuilder()
            .addPathSegment("configs").addPathSegment(id).build()
        exec("DELETE", url, ByteArray(0))
        true
    }

    suspend fun vote(id: String, up: Boolean): VoteResult = withContext(Dispatchers.IO) {
        val url = requireBase().newBuilder().addPathSegment("configs").addPathSegment(id)
            .addPathSegment("vote").build()
        val body = JSONObject().put("value", if (up) 1 else -1).toString().toByteArray()
        json.decodeFromString(VoteResult.serializer(), exec("POST", url, body))
    }

    suspend fun report(id: String, reason: String): Boolean = withContext(Dispatchers.IO) {
        val url = requireBase().newBuilder().addPathSegment("configs").addPathSegment(id)
            .addPathSegment("report").build()
        val body = JSONObject().put("reason", reason).toString().toByteArray()
        exec("POST", url, body)
        true
    }

    private fun exec(method: String, url: HttpUrl, body: ByteArray?): String {
        val bodyBytes = body ?: ByteArray(0)
        val handle = UploaderIdentity.handle(context)
        val googleBacked = UploaderIdentity.isGoogleBacked()
        val headers = RequestSigner.headers(
            method, url.encodedPath, bodyBytes, handle, googleBacked,
        )
        val builder = Request.Builder().url(url)
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete(bodyBytes.toRequestBody(JSON_MEDIA))
            else -> builder.method(method, bodyBytes.toRequestBody(JSON_MEDIA))
        }
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.peekBody(MAX_RESPONSE_BYTES).string()
            if (!resp.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrDefault("")
                throw IOException(if (detail.isNotBlank()) detail else "HTTP ${resp.code}")
            }
            return text
        }
    }
}
