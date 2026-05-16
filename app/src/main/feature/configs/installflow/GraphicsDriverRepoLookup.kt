package com.winlator.cmod.feature.configs.installflow

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asset entry from any of the configured graphics-driver GitHub repos.
 *
 * Mirrors the data shape the Drivers screen ([com.winlator.cmod.feature.settings
 * .drivers.DriversFragment.fetchGithubReleases]) exposes, but flattened across
 * all repos + releases so the import detector can match a requested driver
 * identifier against every available asset in a single pass.
 *
 *  - [assetName]: GitHub asset filename, e.g. `turnip_24.2.0_adreno6xx.zip`.
 *  - [downloadUrl]: direct download URL the asset is served from.
 *  - [publishedAt]: ISO-8601 timestamp of the release the asset belongs to,
 *    used to pick the newest match when several assets fit a requested
 *    identifier (nightly-style substitution).
 */
data class DriverAssetCandidate(
    val repoName: String,
    val assetName: String,
    val downloadUrl: String,
    val publishedAt: String,
)

/**
 * Read the user's saved graphics-driver repos + fetch every release asset
 * across all of them. Always best-effort — a repo that errors / times out is
 * silently skipped so a single broken repo doesn't poison the whole import.
 *
 * The repo list is the same one [com.winlator.cmod.feature.settings.drivers
 * .DriversFragment] persists under SharedPreferences key `custom_driver_repos`;
 * when the user has never visited the Drivers screen the defaults are
 * applied automatically.
 */
object GraphicsDriverRepoLookup {
    private const val TAG = "DriverRepoLookup"

    // Default GitHub API endpoints — kept in sync with DriversFragment's
    // `defaultRepoList()`. Duplicated here so the import flow doesn't have to
    // construct a DriversFragment instance just to read its defaults.
    private val DEFAULT_REPO_API_URLS = listOf(
        "WinNative Drivers" to
            "https://api.github.com/repos/WinNative-Emu/Drivers/releases",
        "Adreno-Tools-Drivers" to
            "https://api.github.com/repos/StevenMXZ/Adreno-Tools-Drivers/releases",
        "freedreno_turnip-CI" to
            "https://api.github.com/repos/whitebelyash/freedreno_turnip-CI/releases",
    )

    fun fetchAllCandidates(context: Context): List<DriverAssetCandidate> {
        val repos = readRepos(context)
        if (repos.isEmpty()) return emptyList()
        return repos.flatMap { (name, apiUrl) ->
            runCatching { fetchRepoCandidates(name, apiUrl) }
                .onFailure { Timber.tag(TAG).w(it, "Failed to fetch repo $apiUrl") }
                .getOrDefault(emptyList())
        }
    }

    private fun readRepos(context: Context): List<Pair<String, String>> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val savedJson = prefs.getString("custom_driver_repos", null)
            ?: return DEFAULT_REPO_API_URLS
        return runCatching {
            val arr = JSONArray(savedJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "Unknown")
                    val apiUrl = obj.optString("apiUrl", "")
                    if (apiUrl.isNotBlank()) add(name to apiUrl)
                }
            }
        }.onFailure { Timber.tag(TAG).w(it, "Could not parse custom_driver_repos") }
            .getOrDefault(DEFAULT_REPO_API_URLS)
    }

    private fun fetchRepoCandidates(
        repoName: String,
        apiUrl: String,
    ): List<DriverAssetCandidate> {
        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "WinNative")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                Timber.tag(TAG).w("Driver repo %s returned %d", apiUrl, code)
                return emptyList()
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseReleases(repoName, body)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun parseReleases(repoName: String, responseBody: String): List<DriverAssetCandidate> {
        val arr = runCatching { JSONArray(responseBody) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<DriverAssetCandidate>()
        for (i in 0 until arr.length()) {
            val release = arr.optJSONObject(i) ?: continue
            val publishedAt = release.optString("published_at", "")
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                // Only .zip assets are installable by AdrenotoolsManager — same
                // filter the Drivers screen applies in DriversFragment.toZipAssets().
                if (name.lowercase().endsWith(".zip") && url.isNotBlank()) {
                    out += DriverAssetCandidate(repoName, name, url, publishedAt)
                }
            }
        }
        return out
    }
}
