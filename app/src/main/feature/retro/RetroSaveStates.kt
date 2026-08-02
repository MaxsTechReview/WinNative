package com.winlator.cmod.feature.retro

import android.content.Context
import org.json.JSONObject
import java.io.File

object RetroSaveStates {
    const val SLOT_COUNT = 10

    data class SlotInfo(
        val slot: Int,
        val customName: String?,
        val timestampMs: Long,
        val exists: Boolean,
        val sizeBytes: Long,
    )

    fun safeName(gameName: String): String = gameName.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun cloudGameId(
        systemId: String?,
        gameName: String,
    ): String = "retro_${systemId ?: "unknown"}_${safeName(gameName)}"

    fun gameDir(
        context: Context,
        gameName: String,
    ): File {
        val dir = File(context.filesDir, "retro/games/${safeName(gameName)}")
        dir.mkdirs()
        return dir
    }

    fun slotFile(
        context: Context,
        gameName: String,
        slot: Int,
    ): File = File(gameDir(context, gameName), "slot$slot.state")

    fun cloudDir(
        context: Context,
        gameName: String,
    ): File {
        val dir = File(gameDir(context, gameName), "sram")
        dir.mkdirs()
        return dir
    }

    fun sramFile(
        context: Context,
        gameName: String,
    ): File = File(cloudDir(context, gameName), "${safeName(gameName)}.srm")

    fun migrateLegacyCloudLayout(context: Context, gameName: String) {
        runCatching {
            val game = gameDir(context, gameName)
            val sram = cloudDir(context, gameName)
            game.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".srm")) {
                    f.copyTo(File(sram, f.name), overwrite = true)
                    f.delete()
                }
            }
        }
    }

    private fun metaFile(dir: File): File = File(dir, "slots.json")

    private fun readMeta(dir: File): JSONObject =
        runCatching { JSONObject(metaFile(dir).readText()) }.getOrDefault(JSONObject())

    private fun writeMetaAtomic(
        dir: File,
        meta: JSONObject,
    ) {
        runCatching {
            val tmp = File(dir, "slots.json.tmp")
            tmp.writeText(meta.toString())
            if (!tmp.renameTo(metaFile(dir))) {
                metaFile(dir).writeText(meta.toString())
                tmp.delete()
            }
        }
    }

    fun recordIdentity(
        context: Context,
        gameName: String,
        systemId: String?,
    ) {
        val dir = gameDir(context, gameName)
        val meta = readMeta(dir)
        if (meta.optString("game") == gameName && meta.optString("system") == (systemId ?: "")) return
        meta.put("game", gameName)
        meta.put("system", systemId ?: "")
        writeMetaAtomic(dir, meta)
    }

    fun migrateLegacy(
        context: Context,
        gameName: String,
    ) {
        runCatching {
            val dir = gameDir(context, gameName)
            val safe = safeName(gameName)
            val legacyState = File(File(context.filesDir, "retro/states"), "$safe.state")
            val slot1 = File(dir, "slot1.state")
            if (legacyState.isFile && !slot1.exists()) legacyState.renameTo(slot1)
            val legacySram = File(File(context.filesDir, "retro/saves"), "$safe.srm")
            val sram = sramFile(context, gameName)
            if (legacySram.isFile && !sram.exists()) legacySram.renameTo(sram)
            val flatSram = File(dir, "$safe.srm")
            if (flatSram.isFile && !sram.exists()) flatSram.renameTo(sram)
        }
    }

    fun listSlots(
        context: Context,
        gameName: String,
    ): List<SlotInfo> {
        val dir = gameDir(context, gameName)
        val names = readMeta(dir).optJSONObject("names") ?: JSONObject()
        return (1..SLOT_COUNT).map { slot ->
            val file = File(dir, "slot$slot.state")
            SlotInfo(
                slot = slot,
                customName = names.optString(slot.toString()).ifEmpty { null },
                timestampMs = if (file.isFile) file.lastModified() else 0L,
                exists = file.isFile,
                sizeBytes = if (file.isFile) file.length() else 0L,
            )
        }
    }

    fun writeSlot(
        context: Context,
        gameName: String,
        slot: Int,
        bytes: ByteArray,
    ): Boolean =
        runCatching {
            require(bytes.isNotEmpty())
            val target = slotFile(context, gameName, slot)
            val tmp = File(target.parentFile, "slot$slot.state.tmp")
            tmp.writeBytes(bytes)
            if (tmp.length() != bytes.size.toLong()) {
                tmp.delete()
                return false
            }
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            true
        }.getOrDefault(false)

    fun readSlot(
        context: Context,
        gameName: String,
        slot: Int,
    ): ByteArray? =
        runCatching {
            val file = slotFile(context, gameName, slot)
            if (!file.isFile || file.length() == 0L) return null
            file.readBytes()
        }.getOrNull()

    fun renameSlot(
        context: Context,
        gameName: String,
        slot: Int,
        name: String?,
    ) {
        val dir = gameDir(context, gameName)
        val meta = readMeta(dir)
        val names = meta.optJSONObject("names") ?: JSONObject()
        if (name.isNullOrBlank()) names.remove(slot.toString()) else names.put(slot.toString(), name.take(48))
        meta.put("names", names)
        writeMetaAtomic(dir, meta)
    }

    fun hasAnySave(
        context: Context,
        gameName: String,
    ): Boolean {
        if (sramFile(context, gameName).isFile) return true
        return (1..SLOT_COUNT).any { slotFile(context, gameName, it).isFile }
    }

    fun latestLocalTimestamp(
        context: Context,
        gameName: String,
    ): Long {
        var latest = 0L
        val sram = sramFile(context, gameName)
        if (sram.isFile) latest = maxOf(latest, sram.lastModified())
        (1..SLOT_COUNT).forEach { slot ->
            val file = slotFile(context, gameName, slot)
            if (file.isFile) latest = maxOf(latest, file.lastModified())
        }
        return latest
    }

    fun relativeTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return "Empty"
        val delta = System.currentTimeMillis() - timestampMs
        if (delta < 60_000L) return "just now"
        val minutes = delta / 60_000L
        if (minutes < 60) return "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        val hours = minutes / 60
        val remMinutes = minutes % 60
        if (hours < 24) {
            return if (remMinutes > 0) {
                "$hours hour${if (hours == 1L) "" else "s"} $remMinutes minute${if (remMinutes == 1L) "" else "s"} ago"
            } else {
                "$hours hour${if (hours == 1L) "" else "s"} ago"
            }
        }
        val days = hours / 24
        val remHours = hours % 24
        return if (remHours > 0) {
            "$days day${if (days == 1L) "" else "s"} $remHours hour${if (remHours == 1L) "" else "s"} ago"
        } else {
            "$days day${if (days == 1L) "" else "s"} ago"
        }
    }
}
