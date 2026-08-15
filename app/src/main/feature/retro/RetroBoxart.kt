package com.winlator.cmod.feature.retro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

object RetroBoxart {
    private const val BASE = "https://thumbnails.libretro.com"
    private const val OUT_W = 920
    private const val OUT_H = 430
    private const val INDEX_TTL_MS = 7L * 24 * 3600 * 1000

    private val FOLDERS =
        mapOf(
            "nes" to "Nintendo - Nintendo Entertainment System",
            "snes" to "Nintendo - Super Nintendo Entertainment System",
            "gb" to "Nintendo - Game Boy",
            "gbc" to "Nintendo - Game Boy Color",
            "gba" to "Nintendo - Game Boy Advance",
            "genesis" to "Sega - Mega Drive - Genesis",
            "sms" to "Sega - Master System - Mark III",
            "gg" to "Sega - Game Gear",
            "n64" to "Nintendo - Nintendo 64",
            "psx" to "Sony - PlayStation",
            "ps2" to "Sony - PlayStation 2",
            "gc" to "Nintendo - GameCube",
            "wii" to "Nintendo - Wii",
        )

    private data class CaseStyle(
        val body: Int,
        val accent: Int,
        val cartAspect: Float,
        val isDisc: Boolean,
        val labelPortrait: Boolean,
    )

    private val STYLES =
        mapOf(
            "nes" to CaseStyle(0xFF9A9AA2.toInt(), 0xFF3B3B42.toInt(), 0.80f, false, true),
            "snes" to CaseStyle(0xFFB6B1AB.toInt(), 0xFF8A5BA5.toInt(), 1.45f, false, false),
            "gb" to CaseStyle(0xFFB9B8C2.toInt(), 0xFF4A4A55.toInt(), 1.0f, false, true),
            "gbc" to CaseStyle(0xFF6E5FD8.toInt(), 0xFF2F2A66.toInt(), 1.0f, false, true),
            "gba" to CaseStyle(0xFF5F58C7.toInt(), 0xFF29255C.toInt(), 1.5f, false, false),
            "genesis" to CaseStyle(0xFF232329.toInt(), 0xFFC9A24A.toInt(), 0.82f, false, true),
            "sms" to CaseStyle(0xFF24242C.toInt(), 0xFFD03A3A.toInt(), 0.82f, false, true),
            "gg" to CaseStyle(0xFF1C1C22.toInt(), 0xFF3E7BD6.toInt(), 0.95f, false, true),
            "n64" to CaseStyle(0xFF515158.toInt(), 0xFFCF3B3B.toInt(), 1.28f, false, false),
            "psx" to CaseStyle(0xFFE8E8EC.toInt(), 0xFF2F2F36.toInt(), 1.0f, true, false),
            "ps2" to CaseStyle(0xFF1E2C6E.toInt(), 0xFF0E1436.toInt(), 0.74f, true, true),
            "gc" to CaseStyle(0xFF4A4E57.toInt(), 0xFF6B3FA0.toInt(), 0.74f, true, true),
            "wii" to CaseStyle(0xFFE8E8EC.toInt(), 0xFF1BA0D8.toInt(), 0.74f, true, true),
        )

    val artVersion = androidx.compose.runtime.mutableStateOf(0)
    private val working = java.util.concurrent.atomic.AtomicBoolean(false)

    fun ensureArtworkAsync(context: Context) {
        if (!working.compareAndSet(false, true)) return
        val app = context.applicationContext
        Thread {
            try {
                val retro =
                    runCatching {
                        com.winlator.cmod.runtime.container.ContainerManager(app)
                            .loadShortcuts()
                            .filter { RetroShortcuts.isRetroShortcut(it) }
                    }.getOrDefault(emptyList())
                retro.forEach { shortcut ->
                    if (com.winlator.cmod.feature.shortcuts.LibraryShortcutArtwork.findIconArtworkPath(shortcut) != null) return@forEach
                    val existing = shortcut.getExtra("customCoverArtPath")
                    if (existing.isNotBlank() && File(existing).isFile) return@forEach
                    val system = RetroShortcuts.systemForShortcut(shortcut) ?: return@forEach
                    val name = shortcut.getExtra("custom_name", shortcut.name)
                    val uuid = com.winlator.cmod.feature.shortcuts.LibraryShortcutArtwork.ensureShortcutUuid(shortcut)
                    val artFile = com.winlator.cmod.feature.shortcuts.LibraryShortcutArtwork.buildManagedCustomGameArtworkFile(app, uuid)
                    if (fetchAndCompose(app, system, name, artFile)) {
                        shortcut.putExtra("customCoverArtPath", artFile.absolutePath)
                        shortcut.saveData()
                        artVersion.value++
                    }
                }
            } finally {
                working.set(false)
            }
        }.start()
    }

    fun caseArtEnabled(context: Context): Boolean =
        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(context)
            .getBoolean("retro_case_art", true)

    fun setCaseArtEnabled(context: Context, enabled: Boolean) {
        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit().putBoolean("retro_case_art", enabled).apply()
    }

    fun fetchAndCompose(context: Context, system: RetroSystem, gameName: String, outFile: File): Boolean =
        runCatching {
            val boxart = fetchBoxart(context, system, gameName)
            val bmp = compose(context, system, gameName, boxart)
            outFile.parentFile?.mkdirs()
            outFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            bmp.recycle()
            boxart?.recycle()
            outFile.isFile
        }.getOrDefault(false)

    fun composeCustom(context: Context, system: RetroSystem, gameName: String, source: Bitmap, outFile: File): Boolean =
        runCatching {
            val bmp = compose(context, system, gameName, source)
            outFile.parentFile?.mkdirs()
            outFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            bmp.recycle()
            outFile.isFile
        }.getOrDefault(false)

    private fun fetchBoxart(context: Context, system: RetroSystem, gameName: String): Bitmap? {
        val folder = FOLDERS[system.id] ?: return null
        val names = index(context, system.id, folder) ?: return null
        val match = bestMatch(gameName, names) ?: return null
        val url = "$BASE/${encodePath(folder)}/Named_Boxarts/${encodePath(match)}"
        return download(url)
    }

    private fun index(context: Context, sysId: String, folder: String): List<String>? {
        val cache = File(File(context.filesDir, "retro/boxart_index"), "$sysId.txt")
        if (cache.isFile && System.currentTimeMillis() - cache.lastModified() < INDEX_TTL_MS) {
            return cache.readLines().filter { it.isNotBlank() }
        }
        val html =
            runCatching {
                val conn = URL("$BASE/${encodePath(folder)}/Named_Boxarts/").openConnection() as HttpURLConnection
                conn.connectTimeout = 12000
                conn.readTimeout = 30000
                conn.inputStream.bufferedReader().use { it.readText() }
            }.getOrNull() ?: return if (cache.isFile) cache.readLines().filter { it.isNotBlank() } else null
        val names =
            Regex("href=\"([^\"?]+\\.png)\"").findAll(html)
                .map { URLDecoder.decode(it.groupValues[1], "UTF-8") }
                .filter { !it.startsWith("/") }
                .toList()
        if (names.isEmpty()) return null
        cache.parentFile?.mkdirs()
        cache.writeText(names.joinToString("\n"))
        return names
    }

    private fun normalize(name: String): String =
        name.substringBeforeLast('.')
            .replace(Regex("[\\(\\[][^\\)\\]]*[\\)\\]]"), " ")
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun bestMatch(gameName: String, names: List<String>): String? {
        val target = normalize(gameName)
        if (target.isBlank()) return null
        val byNorm = LinkedHashMap<String, MutableList<String>>()
        names.forEach { byNorm.getOrPut(normalize(it)) { ArrayList() }.add(it) }
        val exact = byNorm[target]
        if (exact != null) return preferRegion(exact)
        val targetTokens = target.split(' ').toSet()
        var best: String? = null
        var bestScore = 0f
        byNorm.forEach { (norm, files) ->
            val tokens = norm.split(' ').toSet()
            val overlap = (targetTokens intersect tokens).size.toFloat()
            if (overlap == 0f) return@forEach
            val score = overlap * 2f / (targetTokens.size + tokens.size)
            val boosted = if (norm.startsWith(target) || target.startsWith(norm)) score + 0.3f else score
            if (boosted > bestScore) {
                bestScore = boosted
                best = preferRegion(files)
            }
        }
        return if (bestScore >= 0.6f) best else null
    }

    private fun preferRegion(files: List<String>): String =
        files.firstOrNull { it.contains("(USA", true) }
            ?: files.firstOrNull { it.contains("(World", true) }
            ?: files.firstOrNull { it.contains("(Europe", true) }
            ?: files.first()

    private fun download(url: String): Bitmap? =
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 30000
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

    private fun encodePath(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private fun compose(context: Context, system: RetroSystem, gameName: String, boxart: Bitmap?): Bitmap {
        val style = STYLES[system.id] ?: STYLES.getValue("nes")
        val out = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(0f, 0f, 0f, OUT_H.toFloat(), 0xFF16161E.toInt(), 0xFF0B0B10.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, OUT_W.toFloat(), OUT_H.toFloat(), p)
        p.shader = RadialGradient(OUT_W * 0.5f, OUT_H * 0.42f, OUT_W * 0.5f, 0x2AFFFFFF, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, OUT_W.toFloat(), OUT_H.toFloat(), p)
        p.shader = null

        if (!caseArtEnabled(context) && boxart != null) {
            drawFitted(c, boxart, RectF(0f, 0f, OUT_W.toFloat(), OUT_H.toFloat()), 24f, 0.94f)
            return out
        }

        val caseH = OUT_H * 0.86f
        val caseW = (caseH * style.cartAspect).coerceAtMost(OUT_W * 0.62f)
        val cx = OUT_W * 0.5f
        val top = (OUT_H - caseH) * 0.5f
        val body = RectF(cx - caseW / 2f, top, cx + caseW / 2f, top + caseH)
        val corner = if (style.isDisc) 14f else 22f

        p.color = 0x66000000
        c.drawRoundRect(RectF(body.left + 8f, body.top + 12f, body.right + 8f, body.bottom + 12f), corner, corner, p)

        p.shader = LinearGradient(body.left, body.top, body.left, body.bottom, lighten(style.body, 0.14f), darken(style.body, 0.18f), Shader.TileMode.CLAMP)
        c.drawRoundRect(body, corner, corner, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = darken(style.body, 0.4f)
        c.drawRoundRect(body, corner, corner, p)
        p.style = Paint.Style.FILL

        val label: RectF
        if (style.isDisc) {
            val spineW = body.width() * 0.085f
            p.color = style.accent
            c.drawRoundRect(RectF(body.left, body.top, body.left + spineW + corner, body.bottom), corner, corner, p)
            c.drawRect(RectF(body.left + spineW, body.top, body.left + spineW + corner, body.bottom), p)
            label = RectF(body.left + spineW + 10f, body.top + 10f, body.right - 10f, body.bottom - 10f)
            p.color = 0x30FFFFFF
            c.drawRect(RectF(body.left + spineW + 4f, body.top, body.left + spineW + 8f, body.bottom), p)
        } else {
            p.color = darken(style.body, 0.28f)
            val grooveTop = body.top + body.height() * 0.055f
            for (i in 0 until 3) {
                val y = grooveTop + i * body.height() * 0.028f
                c.drawRoundRect(RectF(body.left + body.width() * 0.12f, y, body.right - body.width() * 0.12f, y + body.height() * 0.012f), 4f, 4f, p)
            }
            val inset = body.width() * 0.10f
            val labelTop = body.top + body.height() * 0.17f
            val labelBottom = body.bottom - body.height() * 0.09f
            label = RectF(body.left + inset, labelTop, body.right - inset, labelBottom)
            p.color = darken(style.body, 0.35f)
            c.drawRoundRect(RectF(label.left - 5f, label.top - 5f, label.right + 5f, label.bottom + 5f), 10f, 10f, p)
        }

        if (boxart != null) {
            drawFitted(c, boxart, label, 8f, 1f)
        } else {
            p.shader = LinearGradient(label.left, label.top, label.left, label.bottom, lighten(style.accent, 0.18f), darken(style.accent, 0.25f), Shader.TileMode.CLAMP)
            c.drawRoundRect(label, 8f, 8f, p)
            p.shader = null
            drawTitle(c, gameName, system.shortName, label)
        }

        p.shader = LinearGradient(body.left, body.top, body.left, body.top + body.height() * 0.20f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(body.left, body.top, body.right, body.top + body.height() * 0.22f), corner, corner, p)
        p.shader = null
        return out
    }

    private fun drawFitted(c: Canvas, bmp: Bitmap, area: RectF, radius: Float, maxFill: Float) {
        val scale = minOf(area.width() * maxFill / bmp.width, area.height() * maxFill / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val dst = RectF(area.centerX() - w / 2f, area.centerY() - h / 2f, area.centerX() + w / 2f, area.centerY() + h / 2f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val path = Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) }
        c.save()
        c.clipPath(path)
        c.drawBitmap(bmp, null, dst, p)
        c.restore()
    }

    private fun drawTitle(c: Canvas, gameName: String, shortName: String, area: RectF) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        var size = area.height() * 0.16f
        p.textSize = size
        val words = gameName.trim().split(Regex("\\s+"))
        var lines = wrap(words, p, area.width() * 0.88f)
        while (lines.size > 3 && size > area.height() * 0.09f) {
            size *= 0.88f
            p.textSize = size
            lines = wrap(words, p, area.width() * 0.88f)
        }
        if (lines.size > 3) lines = lines.take(3)
        val lineH = p.textSize * 1.18f
        var y = area.centerY() - (lines.size - 1) * lineH / 2f + p.textSize * 0.35f
        lines.forEach {
            c.drawText(it, area.centerX(), y, p)
            y += lineH
        }
        p.isFakeBoldText = false
        p.textSize = area.height() * 0.08f
        p.color = 0xB3FFFFFF.toInt()
        c.drawText(shortName.uppercase(Locale.US), area.centerX(), area.bottom - area.height() * 0.06f, p)
    }

    private fun wrap(words: List<String>, p: Paint, maxW: Float): List<String> {
        val lines = ArrayList<String>()
        var line = StringBuilder()
        words.forEach { w ->
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (p.measureText(candidate) <= maxW || line.isEmpty()) {
                line = StringBuilder(candidate)
            } else {
                lines.add(line.toString())
                line = StringBuilder(w)
            }
        }
        if (line.isNotEmpty()) lines.add(line.toString())
        return lines
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val inv = 1f - f
        return Color.argb(
            255,
            (Color.red(a) * inv + Color.red(b) * f).toInt(),
            (Color.green(a) * inv + Color.green(b) * f).toInt(),
            (Color.blue(a) * inv + Color.blue(b) * f).toInt(),
        )
    }

    private fun lighten(color: Int, f: Float) = mix(color, Color.WHITE, f)

    private fun darken(color: Int, f: Float) = mix(color, Color.BLACK, f)
}
