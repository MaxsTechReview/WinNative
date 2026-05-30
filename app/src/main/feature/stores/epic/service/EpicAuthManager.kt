package com.winlator.cmod.feature.stores.epic.service
import android.content.Context
import com.winlator.cmod.feature.stores.common.Store
import com.winlator.cmod.feature.stores.common.StoreAuthStatus
import com.winlator.cmod.feature.stores.common.StoreSessionBus
import com.winlator.cmod.feature.stores.common.StoreSessionEvent
import com.winlator.cmod.feature.stores.epic.data.EpicCredentials
import com.winlator.cmod.feature.stores.epic.data.EpicGameToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/** Manages Epic Games authentication and account operations. */
object EpicAuthManager {
    private val _isLoggedInFlow = MutableStateFlow(false)
    val isLoggedInFlow = _isLoggedInFlow.asStateFlow()

    // Ownership tokens are valid for about 30 minutes and the endpoint is rate-limited.
    // Cache slightly below that window to avoid burning quota on relaunches.
    private const val OWNERSHIP_TOKEN_CACHE_TTL_MS = 25L * 60L * 1000L

    fun updateLoginStatus(context: Context) {
        _isLoggedInFlow.value = isLoggedIn(context)
    }

    private fun getCredentialsFilePath(context: Context): String {
        val dir = File(context.filesDir, "epic")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "credentials.json").absolutePath
    }

    private fun ownershipTokenCacheFile(context: Context, namespace: String, catalogItemId: String): File {
        val dir = File(context.filesDir, "epic/ownership_tokens").also { it.mkdirs() }
        return File(dir, "${namespace.sanitizeForFilename()}_${catalogItemId.sanitizeForFilename()}.hex")
    }

    private fun readCachedOwnershipTokenHex(context: Context, namespace: String, catalogItemId: String): String? {
        val file = ownershipTokenCacheFile(context, namespace, catalogItemId)
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() >= OWNERSHIP_TOKEN_CACHE_TTL_MS) return null
        return runCatching { file.readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writeOwnershipTokenHex(context: Context, namespace: String, catalogItemId: String, hex: String) {
        runCatching {
            ownershipTokenCacheFile(context, namespace, catalogItemId).writeText(hex)
        }.onFailure { Timber.tag("Epic").w(it, "Failed caching ownership token for $namespace:$catalogItemId") }
    }

    private fun clearOwnershipTokenCache(context: Context) {
        runCatching {
            File(context.filesDir, "epic/ownership_tokens").listFiles()?.forEach { it.delete() }
        }.onFailure { Timber.tag("Epic").w(it, "Failed clearing ownership token cache") }
    }

    fun hasStoredCredentials(context: Context): Boolean {
        val credentialsFile = File(getCredentialsFilePath(context))
        return credentialsFile.exists()
    }

    /**
     * Classifies the stored Epic credentials and clears them when the refresh token is expired.
     *
     * Safe to call from any thread; this only touches local disk.
     */
    fun getAuthStatus(context: Context): StoreAuthStatus {
        if (!hasStoredCredentials(context)) return StoreAuthStatus.LOGGED_OUT
        val credentials =
            loadCredentials(context) ?: run {
                clearStoredCredentials(context)
                return StoreAuthStatus.LOGGED_OUT
            }

        val now = System.currentTimeMillis()
        val accessBuffer = 5L * 60 * 1000 // 5 minutes
        val refreshBuffer = 60L * 1000 // 1 minute

        if (credentials.refreshExpiresAt > 0 && now >= credentials.refreshExpiresAt - refreshBuffer) {
            Timber.i("Epic refresh token expired (refreshExpiresAt=${credentials.refreshExpiresAt}), clearing credentials")
            clearStoredCredentials(context)
            // Startup can call getAuthStatus before bus collectors are active. The login flow
            // handles startup state; refresh failures emit mid-session expiry events.
            return StoreAuthStatus.EXPIRED
        }

        return when {
            credentials.expiresAt > 0 && now < credentials.expiresAt - accessBuffer ->
                StoreAuthStatus.ACTIVE
            credentials.refreshExpiresAt > 0 ->
                StoreAuthStatus.REFRESHABLE
            else ->
                // Legacy credentials without refresh_expires_at are probed on next use.
                StoreAuthStatus.UNKNOWN
        }
    }

    @JvmStatic
    fun isLoggedIn(context: Context): Boolean = getAuthStatus(context).isLoggedInForUi

    /** Clears stored credentials for logout. */
    fun clearStoredCredentials(context: Context): Boolean =
        try {
            clearOwnershipTokenCache(context)
            val authFile = File(getCredentialsFilePath(context))
            val result =
                if (authFile.exists()) {
                    authFile.delete()
                } else {
                    true
                }
            // The session is gone, so the periodic refresh worker can stop.
            EpicTokenRefreshWorker.cancel(context)
            updateLoginStatus(context)
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear Epic credentials")
            false
        }

    /** Extracts an authorization code from a redirect URL or raw code input. */
    private fun extractCodeFromInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http")) {
            val codeMatch = Regex("[?&]code=([^&]+)").find(trimmed)
            return codeMatch?.groupValues?.get(1) ?: ""
        }
        // Raw codes are accepted as-is.
        return trimmed
    }

    /**
     * Authenticates with an Epic OAuth authorization code and stores the resulting credentials.
     */
    suspend fun authenticateWithCode(
        context: Context,
        authorizationCode: String,
    ): Result<EpicCredentials> {
        return try {
            Timber.i("Starting Epic authentication with authorization code...")

            val actualCode = extractCodeFromInput(authorizationCode)
            if (actualCode.isEmpty()) {
                return Result.failure(Exception("Invalid authorization URL: no code parameter found"))
            }

            Timber.d("Authenticating via EpicAuthClient...")

            val authResult = EpicAuthClient.authenticateWithCode(actualCode)

            if (authResult.isFailure) {
                val error = authResult.exceptionOrNull()
                Timber.e(error, "Epic authentication failed: ${error?.message}")
                return Result.failure(error ?: Exception("Authentication failed"))
            }

            val authResponse = authResult.getOrNull()!!

            val credentials =
                EpicCredentials(
                    accessToken = authResponse.accessToken,
                    refreshToken = authResponse.refreshToken,
                    accountId = authResponse.accountId,
                    displayName = authResponse.displayName,
                    expiresAt = authResponse.expiresAt,
                    refreshExpiresAt = authResponse.refreshExpiresAt,
                )

            saveCredentials(context, credentials)

            Timber.i("Epic authentication successful: ${credentials.displayName}")
            Result.success(credentials)
        } catch (e: Exception) {
            Timber.e(e, "Epic authentication exception: ${e.message}")
            Result.failure(Exception("Authentication exception: ${e.message}", e))
        }
    }

    suspend fun getStoredCredentials(context: Context): Result<EpicCredentials> {
        return try {
            if (!hasStoredCredentials(context)) {
                return Result.failure(Exception("No stored credentials found"))
            }

            val credentials = loadCredentials(context)
            if (credentials == null) {
                return Result.failure(Exception("Failed to load credentials"))
            }

            val now = System.currentTimeMillis()
            val expiresAt = credentials.expiresAt
            val bufferMs = 5 * 60 * 1000 // 5 minutes

            if (now + bufferMs >= expiresAt) {
                Timber.d("Access token expired, refreshing...")

                val refreshResult = EpicAuthClient.refreshAccessToken(credentials.refreshToken)

                if (refreshResult.isFailure) {
                    val error = refreshResult.exceptionOrNull()
                    Timber.e(error, "Failed to refresh Epic token — clearing credentials")
                    clearStoredCredentials(context)
                    StoreSessionBus.emit(
                        StoreSessionEvent.SessionExpired(Store.EPIC, error?.message ?: "refresh_failed"),
                    )
                    return Result.failure(Exception("Epic session expired: ${error?.message}", error))
                }

                val authResponse = refreshResult.getOrNull()!!
                val refreshedCredentials =
                    EpicCredentials(
                        accessToken = authResponse.accessToken,
                        refreshToken = authResponse.refreshToken,
                        accountId = authResponse.accountId,
                        displayName = authResponse.displayName,
                        expiresAt = authResponse.expiresAt,
                        refreshExpiresAt = authResponse.refreshExpiresAt,
                    )

                saveCredentials(context, refreshedCredentials)
                StoreSessionBus.emit(StoreSessionEvent.SessionRefreshed(Store.EPIC))
                Timber.i("Access token refreshed successfully")

                return Result.success(refreshedCredentials)
            }

            Result.success(credentials)
        } catch (e: Exception) {
            Timber.e(e, "Error getting Epic credentials: ${e.message}")
            Result.failure(Exception("Error getting credentials: ${e.message}", e))
        }
    }

    /** Gets a launch token immediately before starting a game that uses Epic services. */
    suspend fun getGameLaunchToken(
        context: Context,
        namespace: String? = null,
        catalogItemId: String? = null,
        requiresOwnershipToken: Boolean = false,
    ): Result<EpicGameToken> {
        return try {
            val credentialsResult = getStoredCredentials(context)
            if (credentialsResult.isFailure) {
                return Result.failure(credentialsResult.exceptionOrNull() ?: Exception("Not authenticated"))
            }

            val credentials = credentialsResult.getOrNull()!!

            Timber.d("Getting game exchange token for launch...")
            val exchangeTokenResult = EpicAuthClient.getGameExchangeToken(credentials.accessToken)
            if (exchangeTokenResult.isFailure) {
                return Result.failure(exchangeTokenResult.exceptionOrNull() ?: Exception("Failed to get exchange token"))
            }
            val exchangeCode = exchangeTokenResult.getOrNull()!!

            var ownershipTokenHex: String? = null
            if (requiresOwnershipToken) {
                if (namespace.isNullOrEmpty() || catalogItemId.isNullOrEmpty()) {
                    return Result.failure(Exception("Namespace and catalogItemId required for ownership token"))
                }

                val cachedHex = readCachedOwnershipTokenHex(context, namespace, catalogItemId)
                if (cachedHex != null) {
                    Timber.d("Using cached ownership token for $namespace:$catalogItemId")
                    ownershipTokenHex = cachedHex
                } else {
                    Timber.d("Getting ownership token for $namespace:$catalogItemId...")
                    val ownershipResult =
                        EpicAuthClient.getOwnershipToken(
                            accessToken = credentials.accessToken,
                            accountId = credentials.accountId,
                            namespace = namespace,
                            catalogItemId = catalogItemId,
                        )

                    if (ownershipResult.isFailure) {
                        val error = ownershipResult.exceptionOrNull()?.message ?: "Unknown error"
                        Timber.e("Failed to get required ownership token: $error")
                        return Result.failure(
                            Exception("Failed to get ownership token for DRM-protected game: $error"),
                        )
                    } else {
                        // Mask bytes before hex encoding to avoid sign extension.
                        val tokenBytes = ownershipResult.getOrNull()!!
                        ownershipTokenHex = tokenBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                        writeOwnershipTokenHex(context, namespace, catalogItemId, ownershipTokenHex)
                        Timber.d("Ownership token obtained (${tokenBytes.size} bytes) and cached")
                    }
                }
            }

            val gameToken =
                EpicGameToken(
                    authCode = exchangeCode,
                    accountId = credentials.accountId,
                    displayName = credentials.displayName,
                    ownershipToken = ownershipTokenHex,
                )

            Timber.i("Successfully obtained game launch token")
            Result.success(gameToken)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get game launch token")
            Result.failure(e)
        }
    }

    @JvmStatic
    fun logoutSync(context: Context): Boolean = clearStoredCredentials(context)

    suspend fun logout(context: Context): Result<Unit> =
        try {
            val success = clearStoredCredentials(context)
            if (success) {
                Timber.i("Epic credentials cleared")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to clear credentials"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear Epic credentials")
            Result.failure(e)
        } finally {
            updateLoginStatus(context)
        }

    private fun saveCredentials(
        context: Context,
        credentials: EpicCredentials,
    ) {
        try {
            val authFile = File(getCredentialsFilePath(context))
            val json = JSONObject()
            json.put("access_token", credentials.accessToken)
            json.put("refresh_token", credentials.refreshToken)
            json.put("account_id", credentials.accountId)
            json.put("display_name", credentials.displayName)
            json.put("expires_at", credentials.expiresAt)
            json.put("refresh_expires_at", credentials.refreshExpiresAt)

            authFile.writeText(json.toString())
            updateLoginStatus(context)
            // Keep the periodic refresh worker scheduled while credentials exist.
            EpicTokenRefreshWorker.schedule(context)
            Timber.d("Credentials saved to ${authFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save Epic credentials")
        }
    }

    private fun loadCredentials(context: Context): EpicCredentials? {
        return try {
            val file = File(getCredentialsFilePath(context))
            if (!file.exists()) return null

            val json = JSONObject(file.readText())
            EpicCredentials(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                accountId = json.getString("account_id"),
                // Older credentials files may not include display_name.
                displayName = json.optString("display_name", ""),
                expiresAt = json.getLong("expires_at"),
                refreshExpiresAt = json.optLong("refresh_expires_at", 0L),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load credentials")
            null
        }
    }
}
