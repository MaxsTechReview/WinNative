package com.armsx2

import com.armsx2.runtime.MainActivityRuntime

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import java.io.File

object CoverArtStyle {
    private const val KEY = "library.coverArt3d"
    val use3d = mutableStateOf(false)
    fun load() { use3d.value = MainActivityRuntime.prefs.getBoolean(KEY, false) }
    fun set(value: Boolean) {
        use3d.value = value
        MainActivityRuntime.prefs.edit().putBoolean(KEY, value).apply()
    }
}

object GridLabels {
    private const val KEY = "library.showGridNames"
    val show = mutableStateOf(false)
    fun load() { show.value = MainActivityRuntime.prefs.getBoolean(KEY, false) }
    fun set(value: Boolean) {
        show.value = value
        MainActivityRuntime.prefs.edit().putBoolean(KEY, value).apply()
    }
}

object HiddenGames {
    private const val KEY = "library.hiddenGames"
    private const val SHOW_KEY = "library.showHidden"
    val hidden = mutableStateOf<Set<String>>(emptySet())
    val showHidden = mutableStateOf(false)
    private fun keyOf(game: GameInfo) = game.uri.toString()
    fun load() {
        hidden.value = MainActivityRuntime.prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
        showHidden.value = MainActivityRuntime.prefs.getBoolean(SHOW_KEY, false)
    }
    fun isHidden(game: GameInfo) = hidden.value.contains(keyOf(game))
    fun setHidden(game: GameInfo, value: Boolean) {
        val next = hidden.value.toMutableSet().apply { if (value) add(keyOf(game)) else remove(keyOf(game)) }
        hidden.value = next
        MainActivityRuntime.prefs.edit().putStringSet(KEY, next).apply()
    }
    fun setShowHidden(value: Boolean) {
        showHidden.value = value
        MainActivityRuntime.prefs.edit().putBoolean(SHOW_KEY, value).apply()
    }
}

object LibraryTitles {
    private const val KEY = "library.showTitles"
    val show = mutableStateOf(false)
    fun load() { show.value = MainActivityRuntime.prefs.getBoolean(KEY, false) }
    fun set(value: Boolean) {
        show.value = value
        MainActivityRuntime.prefs.edit().putBoolean(KEY, value).apply()
    }
}

object LibraryRecentShelf {
    private const val KEY = "library.showRecentlyPlayed"
    val show = mutableStateOf(true)
    fun load() { show.value = MainActivityRuntime.prefs.getBoolean(KEY, true) }
    fun set(value: Boolean) {
        show.value = value
        MainActivityRuntime.prefs.edit().putBoolean(KEY, value).apply()
    }
}

object LibraryView {
    private const val KEY_LIST = "library.listMode"
    private const val KEY_COLS = "library.gridColumns"
    private const val KEY_ROWS = "library.gridRows"
    const val MAX_COLS = 6
    const val MAX_ROWS = 5
    val listMode = mutableStateOf(false)
    val columns = mutableStateOf(0)
    val rows = mutableStateOf(0)
    fun load() {
        listMode.value = MainActivityRuntime.prefs.getBoolean(KEY_LIST, false)
        columns.value = MainActivityRuntime.prefs.getInt(KEY_COLS, 0).coerceIn(0, MAX_COLS)
        rows.value = MainActivityRuntime.prefs.getInt(KEY_ROWS, 0).coerceIn(0, MAX_ROWS)
    }
    fun setListMode(v: Boolean) {
        listMode.value = v
        MainActivityRuntime.prefs.edit().putBoolean(KEY_LIST, v).apply()
    }
    fun toggleListMode() = setListMode(!listMode.value)
    fun cycleColumns() {
        val next = when {
            columns.value <= 0 -> 2
            columns.value >= MAX_COLS -> 0
            else -> columns.value + 1
        }
        columns.value = next
        MainActivityRuntime.prefs.edit().putInt(KEY_COLS, next).apply()
    }
    fun cycleRows() {
        val next = when {
            rows.value <= 0 -> 2
            rows.value >= MAX_ROWS -> 0
            else -> rows.value + 1
        }
        rows.value = next
        MainActivityRuntime.prefs.edit().putInt(KEY_ROWS, next).apply()
    }
}

enum class GamePlatform(val key: String) {
    PS2("ps2"),
    PS1("ps1");

    companion object {
        fun fromKey(s: String?): GamePlatform =
            if (s == "ps1") PS1 else PS2
    }
}

data class GameInfo(
    val uri: Uri,
    val title: String,
    val serial: String?,
    val compatibility: Int = 0,
    val extension: String = "",
    val platform: GamePlatform = GamePlatform.PS2,
) {
    val coverUrl: String? get() = serial?.let { s ->
        val repo = when (platform) {
            GamePlatform.PS2 -> "ps2-covers"
            GamePlatform.PS1 -> "psx-covers"
        }
        if (CoverArtStyle.use3d.value)
            "https://raw.githubusercontent.com/xlenore/$repo/main/covers/3d/$s.png"
        else
            "https://raw.githubusercontent.com/xlenore/$repo/main/covers/default/$s.jpg"
    }

    val region: String? get() = serial?.let { gameDbRegion(it) ?: regionForSerial(it) }

    val regionFlag: String? get() = region?.let { regionFlagFor(it) }

    val versionTag: String? get() {
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        return name?.let { FilenameParser.versionTokenOf(it) } ?: serial
    }

    val settingsKey: String? get() = serial?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }
}

object CustomCovers {
    val version = mutableStateOf(0)

    @Volatile
    private var cachedCoversRoot: File? = null
    fun coversRoot(context: Context): File =
        cachedCoversRoot ?: File(MainActivityRuntime.assetCopyRoot(context), "covers").also { cachedCoversRoot = it }

    private fun dir(context: Context): File = File(coversRoot(context), "custom")

    private fun filenameStem(game: GameInfo): String? =
        game.uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }

    private fun keys(game: GameInfo): List<String> = buildList {
        game.serial?.takeIf { it.isNotBlank() }?.let { add(it) }
        filenameStem(game)?.let { add(it) }
        game.title.takeIf { it.isNotBlank() }?.let { add(it) }
    }

    fun loadAll(context: Context): Map<String, File> {
        val files = dir(context).listFiles()?.filter { it.isFile && it.length() > 0L } ?: return emptyMap()
        if (files.isEmpty()) return emptyMap()
        val byStem = HashMap<String, File>(files.size)
        for (f in files) byStem.putIfAbsent(f.nameWithoutExtension.lowercase(), f)
        return byStem
    }

    fun matchIn(map: Map<String, File>, game: GameInfo): File? {
        if (map.isEmpty()) return null
        for (k in keys(game)) map[sanitize(k).lowercase()]?.let { return it }
        return null
    }

    fun fileFor(context: Context, game: GameInfo): File? = matchIn(loadAll(context), game)

    private fun targetFor(context: Context, game: GameInfo): File {
        val key = game.serial?.takeIf { it.isNotBlank() }
            ?: filenameStem(game) ?: game.title.ifBlank { "cover" }
        return File(dir(context), sanitize(key) + ".png")
    }

    fun set(context: Context, game: GameInfo, source: Uri): Boolean = runCatching {
        remove(context, game)
        val target = targetFor(context, game)
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(source)?.use { ins ->
            target.outputStream().use { outs -> ins.copyTo(outs) }
        }
        (target.isFile && target.length() > 0L).also { if (it) version.value++ }
    }.getOrDefault(false)

    fun remove(context: Context, game: GameInfo): Boolean {
        val f = fileFor(context, game) ?: return false
        return f.delete().also { if (it) version.value++ }
    }

    private fun sanitize(s: String): String =
        s.replace(Regex("""[/\\:*?"<>|\n\r\t]"""), "_").trim().ifEmpty { "cover" }
}

private val gameDbRegionCache = java.util.concurrent.ConcurrentHashMap<String, String>()

fun gameDbRegion(serial: String): String? {
    val cached = gameDbRegionCache.getOrPut(serial) {
        val raw = runCatching { MainActivityRuntime.nativeGetRegionForSerial(serial) }
            .getOrNull().orEmpty()
        mapGameDbRegion(raw) ?: ""
    }
    return cached.takeIf { it.isNotEmpty() }
}

private fun mapGameDbRegion(raw: String): String? {
    val u = raw.trim().uppercase()
    if (u.isEmpty()) return null
    return when {
        u == "PAL-IN" || u.contains("INDIA") -> "India"
        u.startsWith("NTSC-U") -> "USA"
        u.startsWith("NTSC-J") -> "Japan"
        u.startsWith("NTSC-K") -> "Korea"
        u.startsWith("NTSC-HK") -> "Hong Kong"
        u.startsWith("NTSC-C") -> "China"
        u.startsWith("NTSC-A") || u == "NTSC" -> "Asia"
        u.startsWith("PAL") -> "Europe"
        else -> null
    }
}

fun regionForSerial(serial: String): String? = when (serial.take(4).uppercase()) {
    "SLUS", "SCUS", "PBPX", "LSP0" -> "USA"
    "SLES", "SCES", "SLED", "SCED", "SLPN" -> "Europe"
    "SLPS", "SLPM", "SCPS", "SCAJ", "ALCH", "PAPX", "ROSE", "TCPS", "KOEI", "PCPX", "CPCS" -> "Japan"
    "SLKA", "SCKA" -> "Korea"
    "SLAJ" -> "Asia"
    else -> null
}

fun regionFlagFor(region: String): String? = when (region) {
    "USA" -> "🇺🇸"
    "Europe" -> "🇪🇺"
    "Japan" -> "🇯🇵"
    "Korea" -> "🇰🇷"
    "India" -> "🇮🇳"
    "China" -> "🇨🇳"
    "Hong Kong" -> "🇭🇰"
    "Asia" -> "🌏"
    else -> null
}

object FilenameParser {
    private val serialRegex = Regex("""([A-Za-z]{4})[\s_-]?(\d{3})\.?(\d{2})""")
    private val tagsRegex = Regex("""[\[(].*?[\])]""")
    private val versionRegex = Regex("""(?i)\bv\.?\s?(\d{1,2}(?:\.\d{1,2}){1,2})\b""")

    fun versionTokenOf(filename: String): String? =
        versionRegex.find(filename)?.let { "v" + it.groupValues[1] }
    private val whitespaceRegex = Regex("""\s+""")
    private val nonWordRegex = Regex("""[^a-z0-9]+""")

    private data class FilenameAlias(val title: String, val serial: String)

    private fun aliasFor(filenameWithoutExt: String): FilenameAlias? {
        val normalized = filenameWithoutExt
            .lowercase()
            .replace(nonWordRegex, " ")
            .trim()

        if (!normalized.contains("devil may cry 2"))
            return null

        return when {
            normalized.contains("dante") ->
                FilenameAlias("Devil May Cry 2 [Dante Disc]", "SLES-82011")
            normalized.contains("lucia") ->
                FilenameAlias("Devil May Cry 2 [Lucia Disc]", "SLES-82012")
            else -> null
        }
    }

    fun parse(filename: String): Pair<String, String?> {
        val withoutExt = filename.substringBeforeLast('.')
        val match = serialRegex.find(withoutExt)
        val serial = match?.let {
            "${it.groupValues[1].uppercase()}-${it.groupValues[2]}${it.groupValues[3]}"
        }
        if (serial == null) {
            aliasFor(withoutExt)?.let { return it.title to it.serial }
        }
        var title = withoutExt
        if (match != null) title = title.replace(match.value, "")
        title = title.replace(tagsRegex, "")
            .replace(whitespaceRegex, " ")
            .trim(' ', '-', '_', '.')
        if (title.isEmpty()) title = withoutExt
        return title to serial
    }
}
