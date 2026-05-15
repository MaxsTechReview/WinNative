package com.winlator.cmod.feature.configs

import android.content.Context
import com.winlator.cmod.feature.configs.data.ConfigRow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level CRUD on the `configs` + `config_votes` Supabase tables. All methods
 * are suspend and dispatch their network calls on Dispatchers.IO.
 *
 * Auth: the client signs in anonymously on first use; the user_id assigned by
 * Supabase is stable per install (anonymous JWT subject). That UUID is what
 * `configs.user_id` and `config_votes.user_id` reference.
 */
@Singleton
class ConfigRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    // Lazy — EncryptedSharedPreferences init can fail on rooted / broken-Keystore
    // devices. Throwing at injection time would crash the Activity that opens Best
    // Configs; deferring lets methods catch the error inside their runCatching and
    // surface a clean Result.failure to the UI.
    private val client: SupabaseClient by lazy { SupabaseClient.getInstance(appContext) }

    /**
     * List configs for a given game, sorted by `vote_count desc, created_at desc`.
     * Returns up to [limit] rows from the `configs_with_votes` view.
     */
    suspend fun listForGame(
        gameSource: String,
        gameId: String,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): Result<List<ConfigRow>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = client.ensureAuthenticated()
            val url = "${client.projectUrl}/rest/v1/configs_with_votes".toHttpUrl().newBuilder()
                .addQueryParameter("select", "*")
                .addQueryParameter("game_source", "eq.$gameSource")
                .addQueryParameter("game_id", "eq.$gameId")
                .addQueryParameter("order", "vote_count.desc,created_at.desc")
                .addQueryParameter("limit", limit.coerceIn(1, MAX_PAGE_SIZE).toString())
                .build()
            val req = client.newRequest(url.toString())
                .header("Accept", "application/json")
                .header(SupabaseClient.HEADER_AUTHORIZATION, "Bearer $token")
                .get()
                .build()
            client.httpClient.newCall(req).execute().use { resp ->
                ensureSuccess(resp)
                val body = resp.body?.string().orEmpty()
                ConfigRow.parseArray(body)
            }
        }
    }

    /**
     * Insert a new config and return its server-assigned id. `user_id` is set to
     * the current anon user automatically — RLS rejects any other value.
     */
    suspend fun uploadConfig(input: UploadConfigInput): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = client.ensureAuthenticated()
            val userId = client.currentUserId() ?: error("no current user after authenticate")
            val body = JSONObject().apply {
                put("user_id", userId)
                put("game_source", input.gameSource)
                put("game_id", input.gameId)
                put("game_name", input.gameName)
                // `uploader_name` is the public-facing identity (GPG display name or
                // "Anonymous"). `custom_name` is left populated with the same value
                // so legacy clients that only know about the old column still see a
                // recognizable display string.
                put("custom_name", input.uploaderName)
                put("uploader_name", input.uploaderName)
                put("device_id", input.deviceId)
                put("device_model", input.deviceModel ?: JSONObject.NULL)
                put("manufacturer", input.manufacturer ?: JSONObject.NULL)
                put("soc_model", input.socModel ?: JSONObject.NULL)
                put("gpu_renderer", input.gpuRenderer ?: JSONObject.NULL)
                put("android_release", input.androidRelease ?: JSONObject.NULL)
                put("android_api", input.androidApi ?: JSONObject.NULL)
                put("app_version", input.appVersion ?: JSONObject.NULL)
                put("config_json", input.configJson)
                put("perf_summary", input.perfSummary ?: JSONObject.NULL)
                put("notes", input.notes ?: JSONObject.NULL)
                put("includes_steam_subtab", input.includesSteamSubtab)
                put("schema_version", input.schemaVersion)
            }
            val req = client.newRequest("/rest/v1/configs")
                .header("Content-Type", "application/json")
                .header(SupabaseClient.HEADER_AUTHORIZATION, "Bearer $token")
                .header("Prefer", "return=representation")
                .post(body.toString().toRequestBody(SupabaseClient.JSON_MEDIA))
                .build()
            client.httpClient.newCall(req).execute().use { resp ->
                ensureSuccess(resp)
                val respBody = resp.body?.string().orEmpty()
                val arr = JSONArray(respBody)
                require(arr.length() > 0) { "Insert returned no rows" }
                arr.getJSONObject(0).getString("id")
            }
        }
    }

    /**
     * Upsert a vote with a direction. [voteType] must be +1 (upvote) or -1 (downvote).
     * Idempotent on repeat — `Prefer: resolution=merge-duplicates` translates a PK
     * conflict into an UPDATE, so a user can flip their vote without a separate
     * unvote-then-revote round trip.
     */
    suspend fun vote(configId: String, voteType: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(voteType == 1 || voteType == -1) { "voteType must be +1 or -1, got $voteType" }
            val token = client.ensureAuthenticated()
            val userId = client.currentUserId() ?: error("no current user after authenticate")
            val body = JSONObject().apply {
                put("config_id", configId)
                put("user_id", userId)
                put("vote_type", voteType)
            }
            val req = client.newRequest("/rest/v1/config_votes")
                .header("Content-Type", "application/json")
                .header(SupabaseClient.HEADER_AUTHORIZATION, "Bearer $token")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body.toString().toRequestBody(SupabaseClient.JSON_MEDIA))
                .build()
            client.httpClient.newCall(req).execute().use { resp ->
                ensureSuccess(resp)
            }
        }
    }

    /** Remove your vote on a config. Idempotent — succeeds even if no vote existed. */
    suspend fun unvote(configId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = client.ensureAuthenticated()
            val userId = client.currentUserId() ?: error("no current user after authenticate")
            val url = "${client.projectUrl}/rest/v1/config_votes".toHttpUrl().newBuilder()
                .addQueryParameter("config_id", "eq.$configId")
                .addQueryParameter("user_id", "eq.$userId")
                .build()
            val req = client.newRequest(url.toString())
                .header(SupabaseClient.HEADER_AUTHORIZATION, "Bearer $token")
                .delete()
                .build()
            client.httpClient.newCall(req).execute().use { resp ->
                ensureSuccess(resp)
            }
        }
    }

    /**
     * Returns a map of (config_id → vote_type ±1) for the current user, restricted to
     * the supplied config ids. Used so the UI can highlight whether the user has
     * upvoted, downvoted, or not voted on each row.
     */
    suspend fun myVotesIn(configIds: List<String>): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            if (configIds.isEmpty()) return@runCatching emptyMap()
            val token = client.ensureAuthenticated()
            val userId = client.currentUserId() ?: return@runCatching emptyMap()
            val inClause = configIds.joinToString(",") { it }
            val url = "${client.projectUrl}/rest/v1/config_votes".toHttpUrl().newBuilder()
                .addQueryParameter("select", "config_id,vote_type")
                .addQueryParameter("user_id", "eq.$userId")
                .addQueryParameter("config_id", "in.($inClause)")
                .build()
            val req = client.newRequest(url.toString())
                .header("Accept", "application/json")
                .header(SupabaseClient.HEADER_AUTHORIZATION, "Bearer $token")
                .get()
                .build()
            client.httpClient.newCall(req).execute().use { resp ->
                ensureSuccess(resp)
                val arr = JSONArray(resp.body?.string().orEmpty())
                buildMap {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        put(obj.getString("config_id"), obj.optInt("vote_type", 1))
                    }
                }
            }
        }
    }

    /**
     * Delete a config row.
     *
     * Server-side authorization (Supabase RLS) is expected to permit deletion when
     * any of the following match the request's authenticated user / claims:
     *   - `configs.user_id == auth.uid()`            (original uploader install)
     *   - `configs.device_id == <client-supplied>`   (same physical device)
     *   - `configs.uploader_name == <client-supplied>` (same GPG-signed-in user)
     *
     * The first match is enforced by the existing RLS policy; the latter two
     * require an updated server-side policy (out of scope for the client) — until
     * that lands, deletions only succeed for the original uploader install. The
     * client still gates the UI to those three checks so the button only appears
     * when the current user can plausibly own the row.
     */
    suspend fun deleteConfig(configId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = client.ensureAuthenticated()
            val url = "${client.projectUrl}/rest/v1/configs".toHttpUrl().newBuilder()
                .addQueryParameter("id", "eq.$configId")
                .build()
            val req = client.newRequest(url.toString())
                .header(SupabaseClient.HEADER_AUTHORIZATION, "Bearer $token")
                .delete()
                .build()
            client.httpClient.newCall(req).execute().use { resp ->
                ensureSuccess(resp)
            }
        }
    }

    /** Returns the user id of the currently signed-in (anonymous or upgraded) user. */
    fun currentUserId(): String? = client.currentUserId()

    private fun ensureSuccess(resp: okhttp3.Response) {
        if (resp.isSuccessful) return
        val body = runCatching { resp.body?.string() }.getOrNull().orEmpty()
        Timber.tag(TAG).w("Supabase request failed ${resp.code}: $body")
        throw IOException("Supabase ${resp.code}: ${body.take(200)}")
    }

    data class UploadConfigInput(
        val gameSource: String,
        val gameId: String,
        val gameName: String,
        /**
         * Public-facing uploader identity. Resolved automatically from Google
         * Play Games (or "Anonymous") — never user-entered, never null/empty.
         */
        val uploaderName: String,
        /**
         * Stable per-install device identifier ([Settings.Secure.ANDROID_ID]).
         * Stored alongside the upload so the same device can later authorize a
         * deletion even when the original Supabase anon `user_id` no longer
         * matches (e.g. after an app reinstall).
         */
        val deviceId: String,
        val deviceModel: String?,
        val manufacturer: String?,
        val socModel: String?,
        val gpuRenderer: String?,
        val androidRelease: String?,
        val androidApi: Int?,
        val appVersion: String?,
        val configJson: JSONObject,
        val perfSummary: JSONObject?,
        val notes: String?,
        val includesSteamSubtab: Boolean,
        val schemaVersion: Int = ConfigSerializer.SCHEMA_VERSION,
    )

    companion object {
        private const val TAG = "ConfigRepository"
        private const val DEFAULT_PAGE_SIZE: Int = 50
        private const val MAX_PAGE_SIZE: Int = 200
    }
}
