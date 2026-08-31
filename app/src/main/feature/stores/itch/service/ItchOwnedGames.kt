package com.winlator.cmod.feature.stores.itch.service

import android.content.Context
import com.winlator.cmod.feature.stores.itch.data.ItchGame
import timber.log.Timber

object ItchOwnedGames {
    private const val PURCHASES_URL = "https://itch.io/my-purchases"

    private val gameLinkRegex = Regex("<a([^>]*href=\"(https://[a-z0-9][a-z0-9_-]*\\.itch\\.io/[a-z0-9][a-z0-9_-]*)[^\"]*\"[^>]*)>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
    private val imgRegex = Regex("<img[^>]*>")
    private val srcRegex = Regex("(?:data-lazy_src|src)=\"([^\"]+)\"")
    private val gameIdRegex = Regex("data-game_id=\"(\\d+)\"")

    fun fetch(
        context: Context,
        page: Int,
    ): List<ItchGame> {
        val url = if (page <= 1) PURCHASES_URL else "$PURCHASES_URL?page=$page"
        val html = ItchWebClient.getHtml(context, url)
        if (!ItchWebClient.isSignedIn(html)) {
            Timber.i("[Itch] owned library requested while signed out")
            return emptyList()
        }
        val cells = ItchCatalog.parseGameCells(html)
        if (cells.isNotEmpty()) return cells
        return parseRows(html)
    }

    fun parseRows(html: String): List<ItchGame> {
        val body = html.substringAfter("<div class=\"main\"", html)
        val games = LinkedHashMap<String, ItchGame>()
        gameLinkRegex.findAll(body).forEach { match ->
            val url = match.groupValues[2].trimEnd('/')
            if (url in games) return@forEach
            val title = ItchCatalog.stripHtml(match.groupValues[3]).trim()
            if (title.isEmpty() || title.length > 120) return@forEach
            val slug = url.substringAfterLast('/')
            if (slug.isEmpty() || slug == "download" || slug == "purchase") return@forEach
            val context = contextAround(body, match.range.first)
            games[url] =
                ItchGame(
                    id = gameIdRegex.find(context)?.groupValues?.get(1)?.toIntOrNull() ?: syntheticId(url),
                    title = title,
                    url = url,
                    coverUrl = coverNear(context),
                    author = url.substringAfter("//").substringBefore(".itch.io"),
                )
        }
        return games.values.toList()
    }

    private fun contextAround(
        html: String,
        index: Int,
    ): String = html.substring((index - 900).coerceAtLeast(0), (index + 900).coerceAtMost(html.length))

    private fun coverNear(block: String): String {
        val img = imgRegex.findAll(block).firstOrNull { it.value.contains("img.itch.zone") } ?: return ""
        return srcRegex.find(img.value)?.groupValues?.get(1).orEmpty()
    }

    private fun syntheticId(url: String): Int = -(url.hashCode().and(Int.MAX_VALUE) % 1_000_000_000).coerceAtLeast(1)
}
