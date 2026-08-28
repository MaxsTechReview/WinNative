package com.winlator.cmod.shared.io

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Rewrites hardcoded GitHub download URLs to a user-configured mirror base
 * (e.g. a Gitee fork: "https://gitee.com/kingokksa") so components/containers
 * can be fetched on networks where github.com is unreachable.
 *
 * The mirror must keep the same repo names, release tags and asset filenames
 * as the original GitHub repos — only the host+owner prefix changes:
 *
 *   https://github.com/{owner}/{repo}/releases/download/{tag}/{file}
 *     -> {base}/{owner}/{repo}/releases/download/{tag}/{file}
 *
 *   https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}
 *     -> {base}/{owner}/{repo}/raw/{branch}/{path}
 *
 * Pref key: "download_source_base" (empty = use GitHub as-is).
 */
object DownloadSource {
    private const val PREF_KEY = "download_source_base"

    fun base(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_KEY, "")
            ?.trim()
            ?.trimEnd('/')
            .orEmpty()

    /** Returns [url] rewritten through the configured mirror base, or [url] unchanged. */
    fun mirroredUrl(context: Context, url: String): String {
        if (url.isBlank()) return url
        val base = base(context)
        if (base.isEmpty()) return url

        // raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}
        //   -> {base}/{owner}/{repo}/raw/{branch}/{path}
        val rawPrefix = "https://raw.githubusercontent.com/"
        if (url.startsWith(rawPrefix)) {
            val rest = url.substring(rawPrefix.length)
            val parts = rest.split('/')
            if (parts.size >= 3) {
                val ownerRepo = parts[0] + "/" + parts[1]
                val branch = parts[2]
                val path = parts.drop(3).joinToString("/")
                return "$base/$ownerRepo/raw/$branch/$path"
            }
        }

        // github.com/{owner}/{repo}/... (releases/download, blob, tree) -> {base}/...
        if (url.startsWith("https://github.com/")) {
            return base + "/" + url.substring("https://github.com/".length)
        }

        return url
    }
}