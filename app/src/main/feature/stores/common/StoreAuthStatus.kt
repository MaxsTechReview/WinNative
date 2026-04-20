package com.winlator.cmod.feature.stores.common

/**
 * Uniform auth lifecycle state for a game-store integration.
 *
 * - [LOGGED_OUT]   no credentials on disk
 * - [ACTIVE]       access token is valid (within a small safety buffer)
 * - [REFRESHABLE]  access token is stale but the refresh token is plausibly still alive
 * - [EXPIRED]      refresh token is past its known lifetime — full re-auth required
 * - [UNKNOWN]      credentials exist but no refresh-window metadata is available
 *                  (e.g. legacy on-disk format). Treat as REFRESHABLE for UI purposes
 *                  and probe the server on the next API call.
 */
enum class StoreAuthStatus {
    LOGGED_OUT,
    ACTIVE,
    REFRESHABLE,
    EXPIRED,
    UNKNOWN,
    ;

    /** True if the UI should treat the user as signed in (session may still need a refresh call). */
    val isLoggedInForUi: Boolean
        get() = this == ACTIVE || this == REFRESHABLE || this == UNKNOWN
}
