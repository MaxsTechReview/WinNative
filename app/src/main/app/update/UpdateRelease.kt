package com.winlator.cmod.app.update

import com.winlator.cmod.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class UpdateChannel(
    val id: String,
) {
    OFFICIAL("official"),
    DEVELOPMENT("development"),
    ;

    companion object {
        fun fromId(id: String?): UpdateChannel = entries.firstOrNull { it.id == id } ?: OFFICIAL
    }
}

data class UpdateNoteEntry(
    val text: String,
    val pullRequest: Int,
    val author: String,
)

data class UpdateNoteSection(
    val title: String,
    val entries: List<UpdateNoteEntry>,
)

data class UpdateRelease(
    val tag: String,
    val name: String,
    val version: AppVersion,
    val preRelease: Boolean,
    val publishedAt: String,
    val htmlUrl: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    val sections: List<UpdateNoteSection>,
) {
    val key: String get() = tag

    val displayDate: String get() = publishedAt.substringBefore('T')

    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/WinNative-Emu/WinNative/releases?per_page=30"
        const val RELEASES_PAGE = "https://github.com/WinNative-Emu/WinNative/releases"

        private val variantName: String
            get() =
                BuildConfig.FLAVOR.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                }

        fun installedVersion(): AppVersion? = AppVersion.parse(BuildConfig.VERSION_NAME)

        fun isReleaseBuild(): Boolean = installedVersion() != null

        fun pick(
            json: String,
            channel: UpdateChannel,
        ): UpdateRelease? {
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
            var best: UpdateRelease? = null
            for (i in 0 until array.length()) {
                val candidate = parse(array.optJSONObject(i) ?: continue) ?: continue
                if (channel == UpdateChannel.OFFICIAL && candidate.preRelease) continue
                if (best == null || candidate.version > best.version) best = candidate
            }
            return best
        }

        private fun parse(o: JSONObject): UpdateRelease? {
            if (o.optBoolean("draft", false)) return null
            val tag = o.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            val version = AppVersion.parse(tag) ?: return null

            val assets = o.optJSONArray("assets") ?: return null
            var apk: JSONObject? = null
            val suffix = "-$variantName-signed.apk"
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(suffix, ignoreCase = true)) {
                    apk = asset
                    break
                }
            }
            if (apk == null) return null

            val url = apk.optString("browser_download_url").takeIf { it.startsWith("https://") } ?: return null

            return UpdateRelease(
                tag = tag,
                name = o.optString("name").takeIf { it.isNotBlank() } ?: tag,
                version = version,
                preRelease = o.optBoolean("prerelease", false),
                publishedAt = o.optString("published_at"),
                htmlUrl = o.optString("html_url"),
                apkName = apk.optString("name"),
                apkUrl = url,
                apkSize = apk.optLong("size", 0L),
                sections = parseNotes(o.optString("body")),
            )
        }

        private val HEADING = Regex("^#{1,4}\\s+(.*)$")
        private val BULLET = Regex("^\\s*[-*]\\s+(.*)$")
        private val PR_REF = Regex("(?:https://github\\.com/[^\\s)]+/pull/(\\d+)|#(\\d+))")
        private val AUTHOR_REF = Regex("@([A-Za-z0-9][A-Za-z0-9-]*)")

        fun parseNotes(body: String?): List<UpdateNoteSection> {
            if (body.isNullOrBlank()) return emptyList()
            val sections = mutableListOf<UpdateNoteSection>()
            var title = "Changes"
            var entries = mutableListOf<UpdateNoteEntry>()

            fun flush() {
                if (entries.isNotEmpty()) {
                    sections += UpdateNoteSection(title, entries.toList())
                    entries = mutableListOf()
                }
            }

            for (raw in body.replace("\r\n", "\n").split('\n')) {
                val line = raw.trimEnd()
                val heading = HEADING.matchEntire(line.trim())
                if (heading != null) {
                    flush()
                    title = cleanHeading(heading.groupValues[1])
                    continue
                }
                val bullet = BULLET.matchEntire(line) ?: continue
                val entry = toEntry(bullet.groupValues[1]) ?: continue
                entries += entry
            }
            flush()
            return sections
        }

        private fun cleanHeading(raw: String): String {
            val trimmed = raw.trim().trim('*', '_', '#', ' ')
            return when {
                trimmed.equals("What's Changed", ignoreCase = true) -> "Changes"
                trimmed.isEmpty() -> "Changes"
                else -> trimmed
            }
        }

        private val CREDIT_LINE =
            Regex("^(.*?)\\s+by\\s+@([A-Za-z0-9][A-Za-z0-9-]*)\\s+in\\s+https://\\S+(.*)$")

        private fun toEntry(raw: String): UpdateNoteEntry? {
            val line = raw.trim()
            if (line.isEmpty()) return null
            if (line.startsWith("Full Changelog", ignoreCase = true)) return null

            val pull =
                PR_REF.find(line)?.let {
                    (it.groupValues[1].takeIf { g -> g.isNotEmpty() } ?: it.groupValues[2]).toIntOrNull()
                } ?: 0

            val credit = CREDIT_LINE.matchEntire(line)
            var text: String
            val author: String
            if (credit != null) {
                text = credit.groupValues[1].trim()
                author = credit.groupValues[2]
                val trailing = credit.groupValues[3].trim()
                if (trailing.isNotEmpty()) text = "$text — $trailing"
            } else {
                author = AUTHOR_REF.find(line)?.groupValues?.get(1).orEmpty()
                text = line.replace(Regex("\\s*in\\s+https://\\S+"), "")
            }

            text = text.replace(Regex("\\s*\\(#\\d+\\)"), "")
            text = text.replace(Regex("\\*\\*|__|`"), "")
            text = text.replace(Regex("\\s{2,}"), " ").trim().trimEnd(',')

            if (text.isEmpty()) return null
            return UpdateNoteEntry(text = text, pullRequest = pull, author = author)
        }
    }
}
