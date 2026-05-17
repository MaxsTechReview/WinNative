package com.winlator.cmod.feature.sync.google

enum class GoogleAuthMode {
    /** Single shot check of cached PGS auth state. No retry, no UI. */
    SILENT,

    /**
     * Patient silent check — retries `isAuthenticated` a few times with short backoff
     * to give the SDK's background bootstrap auth time to complete on cold start. Never
     * calls `signIn()` so it can NEVER show UI. Intended for callers that load on
     * screen entry and would otherwise race the SDK's auto-sign-in.
     */
    RESUME,

    /** Full interactive sign-in: retries plus an explicit `signIn()` call that may show UI. */
    INTERACTIVE,
}
