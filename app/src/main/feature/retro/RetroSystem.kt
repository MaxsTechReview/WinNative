package com.winlator.cmod.feature.retro

import java.util.Locale

data class RetroSystem(
    val id: String,
    val displayName: String,
    val shortName: String,
    val coreFileName: String,
    val extensions: Set<String>,
    val needsBios: Boolean = false,
    val biosFiles: List<String> = emptyList(),
    val badgeLabel: String = shortName,
) {
    val isExternal: Boolean get() = coreFileName.isBlank()
}

object RetroSystems {
    val NES =
        RetroSystem(
            id = "nes",
            displayName = "Nintendo Entertainment System",
            shortName = "NES",
            coreFileName = "libfceumm_libretro_android.so",
            extensions = setOf("nes", "unf", "unif"),
        )
    val SNES =
        RetroSystem(
            id = "snes",
            displayName = "Super Nintendo",
            shortName = "SNES",
            coreFileName = "libsnes9x_libretro_android.so",
            extensions = setOf("smc", "sfc", "swc", "fig", "bs"),
        )
    val GAMEBOY =
        RetroSystem(
            id = "gb",
            displayName = "Game Boy",
            shortName = "GB",
            coreFileName = "libgambatte_libretro_android.so",
            extensions = setOf("gb"),
        )
    val GAMEBOY_COLOR =
        RetroSystem(
            id = "gbc",
            displayName = "Game Boy Color",
            shortName = "GBC",
            coreFileName = "libgambatte_libretro_android.so",
            extensions = setOf("gbc"),
        )
    val GBA =
        RetroSystem(
            id = "gba",
            displayName = "Game Boy Advance",
            shortName = "GBA",
            coreFileName = "libmgba_libretro_android.so",
            extensions = setOf("gba"),
        )

    const val GBA_MULTIPLAYER_CORE = "libgpsp_libretro_android.so"
    const val DOLPHIN_LIBRETRO_CORE = "libdolphin_libretro_android.so"
    val GENESIS =
        RetroSystem(
            id = "genesis",
            displayName = "Sega Genesis / Mega Drive",
            shortName = "Genesis",
            coreFileName = "libgenesis_plus_gx_libretro_android.so",
            extensions = setOf("gen", "md", "smd", "bin"),
            badgeLabel = "SEGA",
        )
    val MASTER_SYSTEM =
        RetroSystem(
            id = "sms",
            displayName = "Sega Master System",
            shortName = "Master System",
            coreFileName = "libgenesis_plus_gx_libretro_android.so",
            extensions = setOf("sms"),
            badgeLabel = "SMS",
        )
    val GAME_GEAR =
        RetroSystem(
            id = "gg",
            displayName = "Sega Game Gear",
            shortName = "Game Gear",
            coreFileName = "libgenesis_plus_gx_libretro_android.so",
            extensions = setOf("gg"),
            badgeLabel = "GG",
        )
    val N64 =
        RetroSystem(
            id = "n64",
            displayName = "Nintendo 64",
            shortName = "N64",
            coreFileName = "libmupen64plus_next_gles3_libretro_android.so",
            extensions = setOf("n64", "z64", "v64"),
        )
    val PSX =
        RetroSystem(
            id = "psx",
            displayName = "Sony PlayStation",
            shortName = "PS1",
            coreFileName = "libmednafen_psx_libretro_android.so",
            extensions = setOf("cue", "chd", "pbp", "m3u", "iso"),
            needsBios = true,
            biosFiles = listOf("scph5501.bin", "scph5500.bin", "scph5502.bin", "scph1001.bin", "scph7001.bin"),
        )

    val PS2 =
        RetroSystem(
            id = "ps2",
            displayName = "Sony PlayStation 2",
            shortName = "PS2",
            coreFileName = "",
            extensions = setOf("iso", "bin", "chd", "cso", "zso", "mdf", "nrg", "img"),
            badgeLabel = "PS2",
        )

    val GAMECUBE =
        RetroSystem(
            id = "gc",
            displayName = "Nintendo GameCube",
            shortName = "GameCube",
            coreFileName = DOLPHIN_LIBRETRO_CORE,
            extensions = setOf("gcm", "tgc", "gcz", "ciso", "dol", "elf", "rvz", "iso", "m3u"),
            badgeLabel = "GameCube",
        )

    val WII =
        RetroSystem(
            id = "wii",
            displayName = "Nintendo Wii",
            shortName = "Wii",
            coreFileName = DOLPHIN_LIBRETRO_CORE,
            extensions = setOf("wbfs", "wad", "rvz", "gcz", "ciso", "iso", "dol", "elf", "m3u"),
            badgeLabel = "Wii",
        )

    val ALL =
        listOf(
            NES, SNES, GAMEBOY, GAMEBOY_COLOR, GBA, GENESIS, MASTER_SYSTEM, GAME_GEAR,
            N64, PSX, PS2, GAMECUBE, WII,
        )

    private val PSX_ONLY_EXTENSIONS = setOf("cue", "chd", "pbp", "m3u")
    private val PS2_ONLY_EXTENSIONS = setOf("cso", "zso", "mdf", "nrg", "img")
    private val GC_ONLY_EXTENSIONS = setOf("gcm", "tgc")
    private val WII_ONLY_EXTENSIONS = setOf("wbfs", "wad")
    private val DOLPHIN_SHARED_EXTENSIONS = setOf("rvz", "gcz", "ciso", "dol", "elf")
    private const val PS2_SIZE_THRESHOLD = 900L * 1024 * 1024
    private const val WII_SIZE_THRESHOLD = 1500L * 1024 * 1024

    val allExtensions: Set<String> = ALL.flatMap { it.extensions }.toSet() - "exe"

    fun fromId(id: String?): RetroSystem? {
        if (id.isNullOrBlank()) return null
        val normalized = id.trim().lowercase(Locale.US)
        return ALL.firstOrNull { it.id == normalized }
    }

    fun fromExtension(extension: String?): RetroSystem? {
        if (extension.isNullOrBlank()) return null
        val ext = extension.trim().lowercase(Locale.US).removePrefix(".")
        if (ext == "exe") return null
        if (ext in WII_ONLY_EXTENSIONS) return WII
        if (ext in GC_ONLY_EXTENSIONS) return GAMECUBE
        if (ext in DOLPHIN_SHARED_EXTENSIONS) return GAMECUBE
        if (ext in PS2_ONLY_EXTENSIONS) return PS2
        if (ext in PSX_ONLY_EXTENSIONS || ext == "iso") return PSX
        return ALL.firstOrNull { ext in it.extensions }
    }

    fun fromRomPath(path: String?): RetroSystem? {
        if (path.isNullOrBlank()) return null
        val ext = path.substringAfterLast('.', "")
        return fromExtension(ext)
    }

    fun detectForFile(path: String): RetroSystem? {
        val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
        if (RetroRomArchive.isArchive(path)) return RetroRomArchive.detect(path)
        if (ext in WII_ONLY_EXTENSIONS) return WII
        if (ext in GC_ONLY_EXTENSIONS) return GAMECUBE
        val size = runCatching { java.io.File(path).length() }.getOrDefault(0L)
        if (ext in DOLPHIN_SHARED_EXTENSIONS) {
            return if (size > WII_SIZE_THRESHOLD) WII else GAMECUBE
        }
        val detected = fromExtension(ext) ?: return null
        if (ext == "bin" && size > 16L * 1024 * 1024) return PSX
        if (detected.id == PSX.id && ext in setOf("iso", "chd", "bin") && size > PS2_SIZE_THRESHOLD) {
            return PS2
        }
        return detected
    }

    fun isRetroRom(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val ext = path.substringAfterLast('.', "").lowercase(Locale.US)
        if (RetroRomArchive.isArchive(path)) return RetroRomArchive.detect(path) != null
        return ext in allExtensions
    }
}
