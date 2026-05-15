package com.winlator.cmod.feature.configs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.winlator.cmod.R
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton client for talking to our Supabase project.
 *
 * Holds the project URL + publishable key, an OkHttpClient that injects the required
 * Supabase REST headers (`apikey` + `Authorization: Bearer <token>`), and the anon
 * auth session state. Anonymous sign-in is automatic on first use; refresh-token
 * rotation is handled here so the rest of the app just calls [accessToken].
 *
 * Security model: the publishable key is safe to embed in the APK by Supabase's
 * explicit design (the `sb_publishable_*` key family replaces the legacy anon JWT
 * and uses row-level-security policies as its enforcement boundary). Refresh tokens
 * are persisted in EncryptedSharedPreferences so a forensic device dump can't lift
 * them in plaintext.
 *
 * Initialize once from `PluviaApp.onCreate(...)` via [init], then call [getInstance]
 * from anywhere else.
 */
class SupabaseClient private constructor(
    private val appContext: Context,
    val projectUrl: String,
    val publishableKey: String,
) {
    private val authMutex = Mutex()
    private val accessTokenRef = AtomicReference<String?>(null)
    @Volatile private var accessTokenExpiresAt: Long = 0L
    @Volatile private var userIdRef: String? = null

    private val encryptedPrefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            ENC_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * OkHttp client that automatically attaches `apikey` (publishable) and
     * `Authorization: Bearer <token>` headers to every request. The bearer is the
     * cached user access token if signed in, otherwise the publishable key — which
     * lets unauthenticated reads work without an explicit auth call.
     */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .build()

    /** Current user UUID (anon or permanent), or null until [ensureAuthenticated] succeeds at least once. */
    fun currentUserId(): String? = userIdRef ?: encryptedPrefs.getString(KEY_USER_ID, null)?.also { userIdRef = it }

    /**
     * Ensure we have a valid, non-expired access token. Signs in anonymously on
     * first use, refreshes when expired, and returns the bearer to use for
     * subsequent requests. Caller is responsible for IO-dispatcher context.
     */
    @Throws(IOException::class)
    suspend fun ensureAuthenticated(): String {
        accessTokenRef.get()?.let { tok ->
            if (System.currentTimeMillis() < accessTokenExpiresAt - REFRESH_LEEWAY_MS) {
                return tok
            }
        }
        authMutex.withLock {
            // Re-check inside the lock — someone else may have refreshed while we waited.
            accessTokenRef.get()?.let { tok ->
                if (System.currentTimeMillis() < accessTokenExpiresAt - REFRESH_LEEWAY_MS) {
                    return tok
                }
            }
            val stored = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
            val response = if (stored != null) {
                runCatching { refreshSession(stored) }.getOrNull() ?: signUpAnonymously()
            } else {
                signUpAnonymously()
            }
            applySession(response)
            return accessTokenRef.get() ?: error("No access token after session setup")
        }
    }

    /** Force a fresh anonymous user. Drops any persisted refresh token. Used on auth-failure recovery. */
    @Throws(IOException::class)
    suspend fun resetSession() {
        authMutex.withLock {
            encryptedPrefs.edit().remove(KEY_REFRESH_TOKEN).remove(KEY_USER_ID).apply()
            accessTokenRef.set(null)
            userIdRef = null
            accessTokenExpiresAt = 0L
            applySession(signUpAnonymously())
        }
    }

    /** Build an authenticated request for the given relative path. Caller adds method + body. */
    fun newRequest(path: String): Request.Builder {
        val url = if (path.startsWith("http")) path else "$projectUrl$path"
        return Request.Builder().url(url)
    }

    /** Helper: POST JSON object body. */
    fun jsonBody(json: JSONObject) = json.toString().toRequestBody(JSON_MEDIA)

    private fun signUpAnonymously(): SessionResponse {
        val req = Request.Builder()
            .url("$projectUrl/auth/v1/signup")
            .header(HEADER_APIKEY, publishableKey)
            .header(HEADER_AUTHORIZATION, "Bearer $publishableKey")
            .header("Content-Type", "application/json")
            .post("{}".toRequestBody(JSON_MEDIA))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = runCatching { resp.body?.string() }.getOrNull()
                throw IOException("anonymous signUp failed (${resp.code}): ${body ?: ""}")
            }
            val body = resp.body?.string() ?: throw IOException("anonymous signUp empty body")
            return SessionResponse.parse(body)
        }
    }

    private fun refreshSession(refreshToken: String): SessionResponse {
        val req = Request.Builder()
            .url("$projectUrl/auth/v1/token?grant_type=refresh_token")
            .header(HEADER_APIKEY, publishableKey)
            .header(HEADER_AUTHORIZATION, "Bearer $publishableKey")
            .header("Content-Type", "application/json")
            .post(JSONObject().put("refresh_token", refreshToken).toString().toRequestBody(JSON_MEDIA))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = runCatching { resp.body?.string() }.getOrNull()
                throw IOException("refresh failed (${resp.code}): ${body ?: ""}")
            }
            val body = resp.body?.string() ?: throw IOException("refresh empty body")
            return SessionResponse.parse(body)
        }
    }

    private fun applySession(s: SessionResponse) {
        accessTokenRef.set(s.accessToken)
        accessTokenExpiresAt = System.currentTimeMillis() + s.expiresInSeconds * 1000L
        userIdRef = s.userId
        // Refresh tokens rotate every refresh — always overwrite the stored token.
        encryptedPrefs.edit()
            .putString(KEY_REFRESH_TOKEN, s.refreshToken)
            .putString(KEY_USER_ID, s.userId)
            .apply()
    }

    /**
     * Interceptor that injects the required Supabase REST headers on every outbound
     * request whose URL starts with our project URL.
     */
    private inner class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            // Only touch requests for our project; leave third-party calls alone.
            if (!req.url.toString().startsWith(projectUrl)) return chain.proceed(req)
            val token = accessTokenRef.get() ?: publishableKey
            val rebuilt = req.newBuilder()
                .apply {
                    if (req.header(HEADER_APIKEY) == null) header(HEADER_APIKEY, publishableKey)
                    if (req.header(HEADER_AUTHORIZATION) == null) header(HEADER_AUTHORIZATION, "Bearer $token")
                }
                .build()
            return chain.proceed(rebuilt)
        }
    }

    /** Lightweight typed view of the Supabase /auth/v1 response payload. */
    internal data class SessionResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long,
        val userId: String,
        val isAnonymous: Boolean,
    ) {
        companion object {
            fun parse(json: String): SessionResponse {
                val obj = JSONObject(json)
                val user = obj.optJSONObject("user") ?: error("missing user in auth response")
                return SessionResponse(
                    accessToken = obj.getString("access_token"),
                    refreshToken = obj.getString("refresh_token"),
                    expiresInSeconds = obj.optLong("expires_in", 3600L),
                    userId = user.getString("id"),
                    isAnonymous = user.optBoolean("is_anonymous", true),
                )
            }
        }
    }

    companion object {
        private const val TAG = "SupabaseClient"
        const val HEADER_APIKEY = "apikey"
        const val HEADER_AUTHORIZATION = "Authorization"

        private const val ENC_PREFS_NAME = "supabase_session_enc"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val REFRESH_LEEWAY_MS: Long = 60_000L

        @JvmField
        val JSON_MEDIA = "application/json".toMediaType()

        @Volatile
        private var INSTANCE: SupabaseClient? = null

        fun init(context: Context) {
            if (INSTANCE != null) return
            synchronized(this) {
                if (INSTANCE != null) return
                val ctx = context.applicationContext
                INSTANCE = SupabaseClient(
                    appContext = ctx,
                    projectUrl = ctx.getString(R.string.supabase_project_url),
                    publishableKey = ctx.getString(R.string.supabase_publishable_key),
                )
                Timber.tag(TAG).i("Supabase client initialized for ${ctx.getString(R.string.supabase_project_url)}")
            }
        }

        fun getInstance(context: Context): SupabaseClient {
            INSTANCE?.let { return it }
            init(context)
            return INSTANCE ?: error("SupabaseClient init failed")
        }
    }
}
