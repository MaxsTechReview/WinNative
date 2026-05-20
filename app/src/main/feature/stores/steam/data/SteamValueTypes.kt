package com.winlator.cmod.feature.stores.steam.data

/**
 * In-house replacements for the small JavaSteam value/data types the app
 * still used (Phase 9 — dropping the `in.dragonbra.javasteam` dependency).
 * Each keeps the exact field/method names the call sites relied on so the
 * migration is just an import swap.
 */

/**
 * A Steam account identifier — the in-house replacement for
 * `in.dragonbra.javasteam.types.SteamID`. Wraps the raw 64-bit SteamID; the
 * low 32 bits are the account id.
 */
class SteamID(
    private val value: Long,
) {
    /** The low 32 bits of the SteamID64 (the "account id"). */
    val accountID: Long
        get() = value and 0xFFFFFFFFL

    /** Whether this looks like a real (non-zero) SteamID. */
    val isValid: Boolean
        get() = value != 0L

    /** The raw 64-bit SteamID. */
    fun convertToUInt64(): Long = value
}

/**
 * A Steam game identifier — in-house replacement for
 * `in.dragonbra.javasteam.types.GameID`. Wraps the raw 64-bit GameID.
 */
class GameID(
    private val value: Long,
) {
    fun convertToUInt64(): Long = value
}

/**
 * A PICS product-info request (app or package id + optional access token) —
 * in-house replacement for `steamapps.PICSRequest`. Used as a data carrier
 * for the C++ WN-Steam-Client PICS pipeline.
 */
data class PICSRequest(
    val id: Int,
    val accessToken: Long = 0L,
)

/**
 * One OS process belonging to a running game — in-house replacement for
 * `steamapps.AppProcessInfo`.
 */
data class AppProcessInfo(
    val processId: Int,
    val processIdParent: Int,
    val parentIsSteam: Boolean,
)

/**
 * A running game reported to Steam (CMsgClientGamesPlayed) — in-house
 * replacement for `steamapps.GamePlayedInfo`, trimmed to the fields the app
 * actually sets/reads.
 */
data class GamePlayedInfo(
    val gameId: Long,
    val processId: Int,
    val ownerId: Int,
    val launchSource: Int,
    val gameBuildId: Int,
    val processIdList: List<AppProcessInfo>,
)

/**
 * In-house replacement for `steamclient.AsyncJobFailedException`. Retained so
 * the (now dormant) retry `catch` blocks around cloud sync still compile;
 * nothing in the C++ path throws it.
 */
class AsyncJobFailedException(
    message: String? = null,
) : Exception(message)
