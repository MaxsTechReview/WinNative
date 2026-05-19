package com.winlator.cmod.feature.stores.steam.wnsteam

import java.util.concurrent.CompletableFuture

/**
 * Mirrors JavaSteam's `in.dragonbra.javasteam.steam.authentication.IAuthenticator`
 * shape exactly. SteamLoginViewModel's existing `object : IAuthenticator { ... }`
 * block becomes `object : WnAuthenticator { ... }` in Phase 2 with zero
 * method-signature changes.
 *
 * The native client invokes these via JNI when Steam asks for a Steam Guard
 * confirmation during BeginAuthSessionViaCredentials / PollAuthSessionStatus.
 */
interface WnAuthenticator {

    /**
     * Steam pushed a "tap to approve" prompt to the user's mobile authenticator.
     * Return a future that completes `true` if the UI should wait for the user
     * to approve out-of-band (always true for our flow — matches IAuthenticator).
     */
    fun acceptDeviceConfirmation(): CompletableFuture<Boolean>

    /**
     * Steam requires a TOTP code from the mobile authenticator.
     * Complete the returned future with the code the user enters.
     * @param previousCodeWasIncorrect true if a prior submission was rejected.
     */
    fun getDeviceCode(previousCodeWasIncorrect: Boolean): CompletableFuture<String>

    /**
     * Steam requires an email Steam Guard code.
     * Complete the returned future with the code the user enters.
     * @param email the email address Steam emailed the code to (may be null).
     * @param previousCodeWasIncorrect true if a prior submission was rejected.
     */
    fun getEmailCode(
        email: String?,
        previousCodeWasIncorrect: Boolean,
    ): CompletableFuture<String>
}
