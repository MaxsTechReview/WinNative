package com.winlator.cmod.shared.io

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Rewrites hardcoded GitHub download URLs to a user-configured mirror so
 * components/containers can be fetched on networks where github.com is
 * unreachable (e.g. a Gitee fork in mainland China).
 *
 * Two mechanisms (both off by default — original URLs are never modified):
 *
 *  1. China mirror toggle ("use_china_mirror"): rewrites the WinNative-Components
 *     repo specifically, mapping the GitHub repo path wholesale to the user's
 *     Gitee fork ("china_mirror_base", default the user's fork). This handles the
 *     renamed fork repo ("winnative-components-cnfork") — a plain host+owner
 *     prefix swap would keep "WinNative-Components" and 404.
 *
 *  2. Legacy custom base ("download_source_base"): replace the "https://github.com/"
 *     and "https://raw.githubusercontent.com/" prefixes with the given base,
 *     keeping owner/repo/branch/path (repo names must match).
 *
 * The mirror must keep the same release tags and asset filenames as the originals.
 */
object DownloadSource {
    private const val PREF_KEY = "download_source_base"
    private const val PREF_CHINA_MIRROR = "use_china_mirror"
    private const val PREF_CHINA_MIRROR_BASE = "china_mirror_base"

    /** The GitHub repo that the China mirror replaces. */
    private const val GITHUB_COMPONENTS_HTTPS = "https://github.com/nicholasx417/WinNative-Components"
    private const val GITHUB_COMPONENTS_RAW = "https://raw.githubusercontent.com/nicholasx417/WinNative-Components/"

    /** Default China mirror: the user's Gitee fork of WinNative-Components. */
    const val DEFAULT_CHINA_MIRROR_BASE = "https://gitee.com/kingokksa/winnative-components-cnfork"

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

    /** Returns [url] rewritten through the configured mirror, or [url] unchanged. */
    fun mirroredUrl(context: Context, url: String): String {
        if (url.isBlank()) return url

        // 1) China mirror: repo-path-level mapping (handles renamed fork repos).
        if (chinaMirrorEnabled(context)) {
            val base = chinaMirrorBase(context)
            if (base.isNotEmpty()) {
                // raw.githubusercontent.com/nicholasx417/WinNative-Components/{branch}/{path}
                //   -> {base}/raw/{branch}/{path}
                if (url.startsWith(GITHUB_COMPONENTS_RAW)) {
                    val rest = url.removePrefix(GITHUB_COMPONENTS_RAW) // branch/path
                    val parts = rest.split('/')
                    if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                        val branch = parts[0]
                        val path = parts.drop(1).joinToString("/")
                        return "$base/raw/$branch/$path"
                    }
                }
                // github.com/nicholasx417/WinNative-Components/... (releases/download, blob, tree)
                //   -> {base}/...
                if (url.startsWith(GITHUB_COMPONENTS_HTTPS)) {
                    return base + url.removePrefix(GITHUB_COMPONENTS_HTTPS)
                }
            }
            // Other GitHub repos are intentionally untouched (default repos preserved).
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