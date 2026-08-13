package com.winlator.cmod.feature.retro

import android.content.Context
import com.armsx2.runtime.MainActivityRuntime
import kr.co.iefriends.pcsx2.NativeApp
import org.json.JSONObject
import java.io.File

object Ps2DnasBypass {
    const val PREF = "wn.ps2.net.dnasbypass"

    @Volatile
    private var db: Map<String, Game>? = null

    private data class Variant(val name: String, val codes: List<String>, val crc: String?, val auto: Boolean)

    private data class Game(val title: String, val variants: List<Variant>)

    private fun loadDb(ctx: Context): Map<String, Game> {
        db?.let { return it }
        val parsed =
            runCatching {
                val text = ctx.assets.open("dnas/dnas_bypass.json").bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val map = HashMap<String, Game>()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val serial = keys.next()
                    val g = root.getJSONObject(serial)
                    val vs = g.getJSONArray("cheats")
                    val variants = ArrayList<Variant>()
                    for (i in 0 until vs.length()) {
                        val v = vs.getJSONObject(i)
                        val codesArr = v.getJSONArray("codes")
                        val codes = (0 until codesArr.length()).map { codesArr.getString(it) }
                        variants.add(
                            Variant(
                                v.getString("name"),
                                codes,
                                v.optString("crc").takeIf { it.isNotBlank() },
                                v.optBoolean("auto", false),
                            ),
                        )
                    }
                    map[serial.uppercase()] = Game(g.getString("title"), variants)
                }
                map
            }.getOrDefault(emptyMap())
        db = parsed
        return parsed
    }

    fun hasBypass(ctx: Context, serial: String?): Boolean =
        serial != null && loadDb(ctx).containsKey(serial.trim().uppercase())

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE).getBoolean(PREF, true)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE).edit().putBoolean(PREF, on).apply()
    }

    data class BypassEntry(val name: String, val body: String, val auto: Boolean)

    private fun disabledKey(serial: String) = "wn.ps2.dnas.disabled.${serial.trim().uppercase()}"

    fun disabledNames(ctx: Context, serial: String): Set<String> {
        val raw = ctx.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE).getString(disabledKey(serial), "") ?: ""
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun setDisabledNames(ctx: Context, serial: String, names: Set<String>) {
        val arr = org.json.JSONArray()
        names.forEach { arr.put(it) }
        ctx.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE).edit()
            .putString(disabledKey(serial), arr.toString()).apply()
    }

    fun ensureSingleDnasEnabled(ctx: Context, serial: String, allNames: Set<String>): Set<String> {
        if (allNames.isEmpty()) return emptySet()
        val disabled = disabledNames(ctx, serial).toMutableSet()
        val enabled = allNames.filter { it !in disabled }
        if (enabled.size <= 1) return disabled
        val keep = enabled.first()
        val next = allNames - keep
        setDisabledNames(ctx, serial, next)
        return next
    }

    fun bypassEntries(ctx: Context, serial: String?): List<BypassEntry> {
        val game = loadDb(ctx)[serial?.trim()?.uppercase()] ?: return emptyList()
        val used = HashMap<String, Int>()
        return game.variants.mapNotNull { v ->
            val base = v.name
            val n = (used[base] ?: 0) + 1
            used[base] = n
            val nm = if (n > 1) "$base ($n)" else base
            val lines = v.codes.mapNotNull { toPatchLine(it) }
            if (lines.isEmpty()) {
                null
            } else {
                val body = buildString {
                    append("[").append(nm).append("]\n")
                    lines.forEach { append(it).append('\n') }
                }
                BypassEntry(nm, body, v.auto)
            }
        }
    }

    private fun toPatchLine(raw: String): String? {
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size != 2) return null
        val a = parts[0].uppercase()
        val v = parts[1].uppercase()
        if (a.length != 8 || v.length != 8) return null
        if (!a.all { it.isDigit() || it in 'A'..'F' } || !v.all { it.isDigit() || it in 'A'..'F' }) return null
        return "patch=1,EE,$a,extended,$v"
    }

    data class Section(val name: String, val body: String, val enabledByDefault: Boolean)

    fun sectionsFor(ctx: Context, serialRaw: String, crcRaw: String): List<Section> {
        val serial = serialRaw.trim().uppercase()
        val crc = crcRaw.trim().uppercase()
        val game = loadDb(ctx)[serial] ?: return emptyList()
        val globalOn = isEnabled(ctx)
        val disabled = disabledNames(ctx, serial)
        val used = HashMap<String, Int>()
        val out = ArrayList<Section>()
        for (v in game.variants) {
            val base = v.name
            val n = (used[base] ?: 0) + 1
            used[base] = n
            val nm = if (n > 1) "$base ($n)" else base
            val lines = v.codes.mapNotNull { toPatchLine(it) }
            if (lines.isEmpty()) continue
            val body = buildString {
                append("[").append(nm).append("]\n")
                lines.forEach { append(it).append('\n') }
            }
            val crcOk = v.crc == null || v.crc.equals(crc, ignoreCase = true)
            val on = globalOn && v.auto && nm !in disabled && crcOk
            out.add(Section(nm, body, on))
        }
        val enabled = out.filter { it.enabledByDefault }
        if (enabled.size <= 1) return out
        val keep = enabled.first().name
        return out.map { if (it.enabledByDefault && it.name != keep) it.copy(enabledByDefault = false) else it }
    }

    fun applyWhenReady(ctx: Context) {
        var tries = 0
        while (tries < 60) {
            val s = runCatching { NativeApp.getGameSerial() }.getOrNull()?.takeIf { it.isNotBlank() }
            val c = runCatching { NativeApp.getGameCRC() }.getOrNull()?.takeIf { it.length == 8 && it != "00000000" }
            if (s != null && c != null) {
                runCatching { Ps2CheatStaging.applyAll(ctx, s, c) }
                    .onFailure { android.util.Log.w("Ps2DnasBypass", "applyAll failed for $s/$c", it) }
                return
            }
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                return
            }
            tries++
        }
    }
}
