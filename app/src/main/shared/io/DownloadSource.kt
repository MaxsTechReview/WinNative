package com.winlator.cmod.shared.io

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Rewrites hardcoded GitHub download URLs through a user-configured China
 * accelerator (a GitHub reverse-proxy such as gh-proxy.com / ghfast.top), so
 * components/containers can be fetched on networks where github.com is slow
 * or unreachable.
 *
 * When the toggle ("use_china_mirror") is on, any https://github.com/ or
 * https://raw.githubusercontent.com/ URL is prefixed with the proxy base
 * ("china_mirror_base", default one of the common ghproxy mirrors). The
 * original URLs are never modified when the toggle is off, and non-GitHub
 * repos are untouched.
 *
 * A legacy free-text base ("download_source_base") is still honored if set
 * manually: it replaces the host prefixes keeping owner/repo/path.
 */
object DownloadSource {
    private const val PREF_KEY = "download_source_base"
    private const val PREF_CHINA_MIRROR = "use_china_mirror"
    private const val PREF_CHINA_MIRROR_BASE = "china_mirror_base"

    /** Default China GitHub proxy. Easily replaced in Settings if it changes. */
    const val DEFAULT_CHINA_MIRROR_BASE = "https://gh-proxy.com"

    fun chinaMirrorEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_CHINA_MIRROR, false)

    fun chinaMirrorBase(context: Context): String {
        val stored =
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_CHINA_MIRROR_BASE, "")
                ?.trim()
                ?.trimEnd('/')
                .orEmpty()
        return stored.ifBlank { DEFAULT_CHINA_MIRROR_BASE }
    }

    private fun customBase(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_KEY, "")
            ?.trim()
            ?.trimEnd('/')
            .orEmpty()

    /** Returns [url] rewritten through the configured accelerator, or [url] unchanged. */
    fun mirroredUrl(context: Context, url: String): String {
        if (url.isBlank()) return url

        // 1) China accelerator: prefix GitHub URLs with the proxy base.
        if (chinaMirrorEnabled(context)) {
            val base = chinaMirrorBase(context)
            if (base.isNotEmpty() &&
                !url.startsWith(base) &&
                (url.startsWith("https://github.com/") ||
                    url.startsWith("https://raw.githubusercontent.com/"))
            ) {
                return base.trimEnd('/') + "/" + url
            }
            return url
        }

        // 2) Legacy custom base: host/owner-prefix swap (repo names must match).
        val custom = customBase(context)
        if (custom.isEmpty()) return url
        val rawPrefix = "https://raw.githubusercontent.com/"
        if (url.startsWith(rawPrefix)) {
            val rest = url.substring(rawPrefix.length)
            val parts = rest.split('/')
            if (parts.size >= 3) {
                val ownerRepo = parts[0] + "/" + parts[1]
                val branch = parts[2]
                val path = parts.drop(3).joinToString("/")
                return "$custom/$ownerRepo/raw/$branch/$path"
            }
        }
        if (url.startsWith("https://github.com/")) {
            return custom + "/" + url.substring("https://github.com/".length)
        }
        return url
    }
}