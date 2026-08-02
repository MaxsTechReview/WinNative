// SPDX-License-Identifier: GPL-3.0+
package com.armsx2

import android.util.Log
import kr.co.iefriends.pcsx2.HttpClient

object PatchRepo {
    private const val TAG = "PatchRepo"
    private const val RAW_BASE = "https://raw.githubusercontent.com/PCSX2/pcsx2_patches/main"
    private const val TREE_URL = "https://api.github.com/repos/PCSX2/pcsx2_patches/git/trees/main?recursive=1"
    private const val USER_AGENT = "ARMSX2"

    private const val GABO_BASE = "https://raw.githubusercontent.com/Gabominated/PCSX2/main"
    private const val GABO_TREE = "https://api.github.com/repos/Gabominated/PCSX2/git/trees/main?recursive=1"
    private const val GABO_DIR = "PCSX2%20Patches"
    @Volatile private var gaboTreeCache: List<String>? = null

    private data class CheatSource(val raw: String, val tree: String)
    private val CHEAT_SOURCES = listOf(
        CheatSource(
            "https://raw.githubusercontent.com/shadowninja826/pcsx2_pnach_cheats/main",
            "https://api.github.com/repos/shadowninja826/pcsx2_pnach_cheats/git/trees/main?recursive=1",
        ),
        CheatSource(
            "https://raw.githubusercontent.com/xs1l3n7x/pcsx2_cheats_collection/main",
            "https://api.github.com/repos/xs1l3n7x/pcsx2_cheats_collection/git/trees/main?recursive=1",
        ),
    )
    private val cheatTreeCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val CRC_RE = Regex("^[0-9A-Fa-f]{8}$")
    private val SERIAL_RE = Regex("^[A-Z]{4}-\\d{5}$")
    private val TREE_PATH_RE = Regex("\"path\"\\s*:\\s*\"([^\"]+\\.pnach)\"")
    private val SECTION_RE = Regex("^\\s*\\[(.+?)]\\s*$")
    private val COMMENT_RE = Regex("^\\s*comment\\s*=\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val GAMETITLE_RE = Regex("(?m)^\\s*gametitle\\s*=\\s*(.+)$")

    @Volatile private var treeCache: List<String>? = null

    data class Entry(
        val name: String,
        val description: String,
        val body: String,
        val source: String,
    )

    data class Result(
        val gametitle: String,
        val entries: List<Entry>,
        val error: String?,
        val serial: String = "",
        val crc: String = "",
    )

    fun fetchForGame(serial: String?, crc: String): Result {
        val c = crc.trim().uppercase()
        if (!CRC_RE.matches(c))
            return Result("", emptyList(), "No game CRC yet — boot the game first.")

        var gametitle = ""
        val entries = mutableListOf<Entry>()

        val patchCandidates = buildList {
            if (!serial.isNullOrBlank()) add("${serial}_$c")
            add(c)
        }
        for (name in patchCandidates) {
            val text = get("$RAW_BASE/patches/$name.pnach") ?: continue
            val (gt, es) = parse(text, "patches")
            if (gametitle.isEmpty()) gametitle = gt
            entries += es
            break
        }

        for (name in patchCandidates) {
            val text = get("$GABO_BASE/$GABO_DIR/$name.pnach") ?: continue
            val (gt, es) = parse(text, "patches")
            if (gametitle.isEmpty()) gametitle = gt
            val seen = entries.mapTo(HashSet()) { it.name }
            for (e in es) if (seen.add(e.name)) entries += e
            break
        }

        fetchCheats(serial, c)?.let { (gt, es) ->
            if (gametitle.isEmpty()) gametitle = gt
            entries += es
        }

        if (entries.isEmpty())
            return Result("", emptyList(), "No patches or cheats in the database for ${serial ?: c}.")
        return Result(gametitle, entries, null, serial?.uppercase().orEmpty(), c)
    }

    private fun fetchCheats(serial: String?, crc: String): Pair<String, List<Entry>>? {
        val c = crc.uppercase()
        val s = serial?.uppercase()
        val haveCrc = CRC_RE.matches(c)
        if (!haveCrc && s == null) return null

        var gametitle = ""
        val entries = mutableListOf<Entry>()
        val seenNames = HashSet<String>()
        for (src in CHEAT_SOURCES) {
            val tree = cheatTree(src)
            if (tree.isEmpty()) continue
            var matches = if (haveCrc)
                tree.filter { it.substringAfterLast('/').uppercase().contains(c) }
            else emptyList()
            if (matches.isEmpty() && s != null)
                matches = tree.filter { it.substringAfterLast('/').uppercase().startsWith("${s}_") }
            for (m in matches) {
                val text = get("${src.raw}/${m.replace(" ", "%20")}") ?: continue
                val (gt, es) = parse(text, "cheats")
                if (gametitle.isEmpty()) gametitle = gt
                for (e in es) if (seenNames.add(e.name)) entries += e
            }
        }
        return if (entries.isEmpty()) null else gametitle to entries
    }

    private fun cheatTree(src: CheatSource): List<String> {
        cheatTreeCache[src.raw]?.let { return it }
        val json = get(src.tree) ?: return emptyList()
        val paths = TREE_PATH_RE.findAll(json).map { it.groupValues[1] }.toList()
        if (paths.isNotEmpty()) cheatTreeCache[src.raw] = paths
        return paths
    }

    fun fetchForSerial(serial: String?): Result {
        val s = serial?.trim()?.uppercase()
        if (s.isNullOrBlank() || !SERIAL_RE.matches(s))
            return Result("", emptyList(), "This game has no serial to search the patch database with.")

        val tree = repoTree()
        if (tree.isEmpty())
            return Result("", emptyList(), "Couldn't reach the PCSX2 patch database. Check your connection.")

        var gametitle = ""
        var resolvedCrc = ""
        val entries = mutableListOf<Entry>()

        val match = tree.firstOrNull { it.startsWith("patches/${s}_", ignoreCase = true) }
        if (match != null) {
            get("$RAW_BASE/$match")?.let { text ->
                val (gt, es) = parse(text, "patches")
                if (gametitle.isEmpty()) gametitle = gt
                entries += es
            }
            resolvedCrc = match.substringAfterLast('/')
                .removeSuffix(".pnach")
                .substringAfter("${s}_", "")
                .substringBefore('_')
                .uppercase()
        }

        gaboTree().firstOrNull { it.substringAfterLast('/').startsWith("${s}_", ignoreCase = true) }
            ?.let { gm ->
                get("$GABO_BASE/${gm.replace(" ", "%20")}")?.let { text ->
                    val (gt, es) = parse(text, "patches")
                    if (gametitle.isEmpty()) gametitle = gt
                    val seen = entries.mapTo(HashSet()) { it.name }
                    for (e in es) if (seen.add(e.name)) entries += e
                }
                if (resolvedCrc.isEmpty())
                    resolvedCrc = gm.substringAfterLast('/').removeSuffix(".pnach")
                        .substringAfter("${s}_", "").substringBefore('_').uppercase()
            }

        fetchCheats(s, resolvedCrc)?.let { (gt, es) ->
            if (gametitle.isEmpty()) gametitle = gt
            entries += es
        }

        if (entries.isEmpty())
            return Result("", emptyList(), "No patches or cheats in the database for $s.")
        return Result(gametitle, entries, null, s, resolvedCrc)
    }

    private fun gaboTree(): List<String> {
        gaboTreeCache?.let { return it }
        val json = get(GABO_TREE) ?: return emptyList()
        val paths = TREE_PATH_RE.findAll(json).map { it.groupValues[1] }.toList()
        if (paths.isNotEmpty()) gaboTreeCache = paths
        return paths
    }

    private fun repoTree(): List<String> {
        treeCache?.let { return it }
        val json = get(TREE_URL) ?: return emptyList()
        val paths = TREE_PATH_RE.findAll(json).map { it.groupValues[1] }.toList()
        if (paths.isNotEmpty()) treeCache = paths
        return paths
    }

    fun buildPnach(gametitle: String, entries: List<Entry>): String = buildString {
        if (gametitle.isNotEmpty()) append("gametitle=").append(gametitle).append("\n\n")
        entries.forEach { append(it.body.trimEnd()).append("\n\n") }
    }

    private fun get(url: String): String? {
        val resp = runCatching { HttpClient.doRequest(url, "GET", null, USER_AGENT, 15000) }
            .getOrElse { Log.w(TAG, "get $url failed: ${it.message}"); return null }
        if (resp.statusCode != 200 || resp.data.isEmpty()) {
            if (resp.statusCode != 404)
                Log.w(TAG, "get $url: status=${resp.statusCode} size=${resp.data.size}")
            return null
        }
        return String(resp.data, Charsets.UTF_8)
    }

    private fun parse(pnach: String, source: String): Pair<String, List<Entry>> {
        val gametitle = GAMETITLE_RE.find(pnach)?.groupValues?.get(1)?.trim().orEmpty()
        val entries = mutableListOf<Entry>()
        var name: String? = null
        var desc = ""
        val body = StringBuilder()
        fun flush() {
            val n = name
            if (n != null) entries.add(Entry(n, desc, body.toString().trimEnd(), source))
            name = null; desc = ""; body.setLength(0)
        }
        for (line in pnach.lines()) {
            val h = SECTION_RE.find(line)
            if (h != null) {
                flush()
                name = h.groupValues[1].trim()
                body.append(line).append('\n')
            } else if (name != null) {
                body.append(line).append('\n')
                if (desc.isEmpty()) COMMENT_RE.find(line)?.let { desc = it.groupValues[1].trim() }
            }
        }
        flush()
        return gametitle to entries
    }

    data class LocalCheat(
        val name: String,
        val description: String,
        val enabled: Boolean,
        val body: String,
    )

    private val PATCH_LINE_RE = Regex("^\\s*/{0,2}\\s*patch\\s*=", RegexOption.IGNORE_CASE)
    private val META_COMMENT_RE = Regex("^[A-Z]{4}-\\d{5}\\s+[0-9A-Fa-f]{8}$")

    fun isPatchCommand(line: String): Boolean = PATCH_LINE_RE.containsMatchIn(line)

    fun parseInstalled(pnach: String, source: String): Pair<String, List<LocalCheat>> {
        val gametitle = GAMETITLE_RE.find(pnach)?.groupValues?.get(1)?.trim().orEmpty()
        val cheats = mutableListOf<LocalCheat>()
        var name: String? = null
        var desc = ""
        val body = StringBuilder()
        var hasPatch = false
        var hasActive = false
        fun flush() {
            val n = name
            if (n != null && hasPatch) cheats.add(LocalCheat(n, desc, hasActive, body.toString().trimEnd()))
            name = null; desc = ""; body.setLength(0); hasPatch = false; hasActive = false
        }
        for (raw in pnach.lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            if (trimmed.isEmpty()) { if (name != null) body.append('\n'); continue }
            if (trimmed.startsWith("gametitle", ignoreCase = true)) continue
            val label = SECTION_RE.find(line)?.groupValues?.get(1)?.trim()
            val isPatch = isPatchCommand(trimmed)
            val commented = trimmed.startsWith("//")
            when {
                label != null -> { flush(); name = label; body.append(line).append('\n') }
                isPatch -> {
                    if (name == null) name = "Unlabelled"
                    hasPatch = true
                    if (!commented) hasActive = true
                    body.append(line).append('\n')
                }
                commented -> {
                    val text = trimmed.trimStart('/').trim()
                    if (text.equals("ARMSX2 manual PNACH", ignoreCase = true) || META_COMMENT_RE.matches(text))
                        continue
                    if (hasPatch) flush()
                    if (name == null) {
                        if (text.isNotEmpty()) { name = text; body.append(line).append('\n') }
                    } else {
                        if (desc.isEmpty()) desc = text
                        body.append(line).append('\n')
                    }
                }
                else -> { if (name != null) body.append(line).append('\n') }
            }
        }
        flush()
        return gametitle to cheats
    }
}
