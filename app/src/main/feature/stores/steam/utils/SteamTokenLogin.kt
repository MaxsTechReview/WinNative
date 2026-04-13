package com.winlator.cmod.feature.stores.steam.utils
import android.annotation.SuppressLint
import com.auth0.android.jwt.JWT
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.runtime.display.environment.components.GuestProgramLauncherComponent
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.io.TarCompressorUtils
import `in`.dragonbra.javasteam.types.KeyValue
import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

const val NULL_CHAR = '\u0000'
const val TOKEN_EXPIRE_TIME = 86400L

class SteamTokenLogin(
    private val steamId: String,
    private val login: String,
    private val token: String,
    private val imageFs: ImageFs,
    private val guestProgramLauncherComponent: GuestProgramLauncherComponent? = null,
) {
    private fun clearCachedClientState() {
        try {
            val localSteamDir = File(imageFs.wineprefix, "drive_c/users/${ImageFs.USER}/AppData/Local/Steam")
            val steamDir = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam")

            listOf(
                File(localSteamDir, "htmlcache"),
                File(steamDir, "appcache/httpcache"),
                File(steamDir, "appcache/librarycache"),
            ).forEach { cacheDir ->
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            Timber.tag("SteamTokenLogin").w(e, "Failed to clear cached Steam client state")
        }
    }

    fun setupSteamFiles(forceFreshClientToken: Boolean = false) {
        SteamUtils.autoLoginUserChanges(imageFs)
        phase1SteamConfig(forceFreshClientToken)
    }

    private fun hdr(): String {
        val crc = CRC32()
        crc.update(login.toByteArray())
        return "${crc.value.toString(16)}1"
    }

    private fun execCommand(command: String): String =
        guestProgramLauncherComponent?.execShellCommand(command, false)
            ?: throw IllegalStateException("GuestProgramLauncherComponent is required for command execution")

    private fun killWineServer() {
        try {
            execCommand("wineserver -k")
        } catch (e: Exception) {
            Timber.tag("SteamTokenLogin").e("Failed to kill wineserver: ${e.message}")
        }
    }

    private fun encryptToken(token: String): String = execCommand("wine ${imageFs.rootDir}/opt/apps/steam-token.exe encrypt $login $token")

    private fun decryptToken(vdfValue: String): String =
        execCommand("wine ${imageFs.rootDir}/opt/apps/steam-token.exe decrypt $login $vdfValue")

    private fun obfuscateToken(
        value: String,
        mtbf: Long,
    ): String = SteamTokenHelper.obfuscate(value.toByteArray(), mtbf)

    private fun deobfuscateToken(
        value: String,
        mtbf: Long,
    ): String = SteamTokenHelper.deobfuscate(value, mtbf)

    @SuppressLint("BinaryOperationInTimber")
    private fun createConfigVdf(): String {
        val hdr = hdr()
        val minMTBF = 1000000000L
        val maxMTBF = 2000000000L
        var mtbf = kotlin.random.Random.nextLong(minMTBF, maxMTBF)
        var encoded = ""

        do {
            try {
                encoded = obfuscateToken("$token$NULL_CHAR", mtbf)
            } catch (_: Exception) {
                mtbf = kotlin.random.Random.nextLong(minMTBF, maxMTBF)
            }
        } while (encoded == "")

        Timber.tag("SteamTokenLogin").d("MTBF: $mtbf")
        Timber.tag("SteamTokenLogin").d("Encoded: $encoded")

        return """
            "InstallConfigStore"
            {
                "Software"
                {
                    "Valve"
                    {
                        "Steam"
                        {
                            "MTBF"		"$mtbf"
                            "ConnectCache"
                            {
                                "$hdr"		"$encoded$NULL_CHAR"
                            }
                            "Accounts"
                            {
                                "$login"
                                {
                                    "SteamID"		"$steamId"
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()
    }

    @SuppressLint("BinaryOperationInTimber")
    private fun createLocalVdf(): String {
        val hdr = hdr()
        val encoded = encryptToken(token)

        return """
            "MachineUserConfigStore"
            {
                "Software"
                {
                    "Valve"
                    {
                        "Steam"
                        {
                            "ConnectCache"
                            {
                                "$hdr"		"$encoded"
                            }
                        }
                    }
                }
            }
            """.trimIndent()
    }

    fun phase1SteamConfig(forceFreshClientToken: Boolean = false) {
        val steamConfigDir = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam/config").toPath()
        Files.createDirectories(steamConfigDir)
        val localSteamDir = File(imageFs.wineprefix, "drive_c/users/${ImageFs.USER}/AppData/Local/Steam").toPath()
        Files.createDirectories(localSteamDir)

        val configVdfPath = steamConfigDir.resolve("config.vdf")
        var shouldWriteConfig = forceFreshClientToken || !Files.exists(configVdfPath)
        var shouldProcessPhase2 = false

        if (!shouldWriteConfig && Files.exists(configVdfPath)) {
            val vdfContent = FileUtils.readString(configVdfPath.toFile()) ?: ""
            if (vdfContent.contains("ConnectCache")) {
                val vdfData = KeyValue.loadFromString(vdfContent)
                val mtbf =
                    vdfData
                        ?.get("Software")
                        ?.get("Valve")
                        ?.get("Steam")
                        ?.get("MTBF")
                        ?.value
                val connectCacheValue =
                    vdfData
                        ?.get("Software")
                        ?.get("Valve")
                        ?.get("Steam")
                        ?.get("ConnectCache")
                        ?.get(hdr())
                        ?.value

                if (mtbf != null && connectCacheValue != null) {
                    try {
                        val decodedToken =
                            deobfuscateToken(connectCacheValue.trimEnd(NULL_CHAR), mtbf.toLong()).trimEnd(NULL_CHAR)
                        if (decodedToken != token) {
                            Timber.tag("SteamTokenLogin").d("Saved JWT differs from current refresh token, overriding config.vdf")
                            shouldWriteConfig = true
                        } else if (JWT(decodedToken).isExpired(TOKEN_EXPIRE_TIME)) {
                            Timber.tag("SteamTokenLogin").d("Saved JWT expired, overriding config.vdf")
                            shouldWriteConfig = true
                        } else {
                            Timber.tag("SteamTokenLogin").d("Saved JWT is not expired, do not override config.vdf")
                            shouldWriteConfig = false
                        }
                    } catch (_: Exception) {
                        Timber.tag("SteamTokenLogin").d("Cannot parse saved JWT, overriding config.vdf")
                        shouldWriteConfig = true
                    }
                } else {
                    if (mtbf == null && connectCacheValue == null) {
                        Timber.tag("SteamTokenLogin").d("MTBF and ConnectCache not found, overriding config.vdf")
                        shouldWriteConfig = true
                    } else if (mtbf != null) {
                        Timber.tag("SteamTokenLogin").d("MTBF exists but ConnectCache not found, processing phase 2")
                        shouldWriteConfig = false
                        shouldProcessPhase2 = true
                    }
                }
            } else if (vdfContent.contains("MTBF")) {
                Timber.tag("SteamTokenLogin").d("MTBF exists but ConnectCache not found, processing phase 2")
                shouldWriteConfig = false
                shouldProcessPhase2 = true
            }
        }

        if (shouldWriteConfig && token.isNotEmpty()) {
            Timber.tag("SteamTokenLogin").d("Overriding config.vdf")
            Files.write(configVdfPath, createConfigVdf().toByteArray())
            clearCachedClientState()

            FileUtils.chmod(File(steamConfigDir.toFile(), "loginusers.vdf"), 505)
            FileUtils.chmod(File(steamConfigDir.toFile(), "config.vdf"), 505)

            if (localSteamDir.resolve("local.vdf").exists()) {
                Files.delete(localSteamDir.resolve("local.vdf"))
            }

            if (guestProgramLauncherComponent == null) {
                Timber.tag("SteamTokenLogin").d("Config updated but launcher is not ready for local.vdf phase 2")
            } else {
                phase2LocalConfig(forceFreshClientToken)
            }
        } else if (shouldProcessPhase2 || forceFreshClientToken || !localSteamDir.resolve("local.vdf").exists()) {
            if (guestProgramLauncherComponent == null) {
                Timber.tag("SteamTokenLogin").d("Skipping phase 2 until launcher is ready")
            } else {
                phase2LocalConfig(forceFreshClientToken)
            }
        }
    }

    fun phase2LocalConfig(forceFreshClientToken: Boolean = false) {
        try {
            val tokenArchive = File(imageFs.rootDir.parentFile, "steam-token.tzst")
            if (!tokenArchive.exists()) {
                Timber.tag("SteamTokenLogin").w("steam-token.tzst is missing, cannot process local.vdf")
                return
            }

            val extractDir = File(imageFs.rootDir, "opt/apps")
            Files.createDirectories(extractDir.toPath())
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, tokenArchive, extractDir)

            val localSteamDir = File(imageFs.wineprefix, "drive_c/users/${ImageFs.USER}/AppData/Local/Steam").toPath()
            Files.createDirectories(localSteamDir)

            if (!forceFreshClientToken && localSteamDir.resolve("local.vdf").exists()) {
                val vdfContent = FileUtils.readString(localSteamDir.resolve("local.vdf").toFile()) ?: ""
                val vdfData = KeyValue.loadFromString(vdfContent)
                val connectCacheValue =
                    vdfData
                        ?.get("Software")
                        ?.get("Valve")
                        ?.get("Steam")
                        ?.get("ConnectCache")
                        ?.get(hdr())
                        ?.value
                if (connectCacheValue != null) {
                    try {
                        val decodedToken = decryptToken(connectCacheValue.trimEnd(NULL_CHAR))
                        if (decodedToken == token && !JWT(decodedToken).isExpired(TOKEN_EXPIRE_TIME)) {
                            Timber.tag("SteamTokenLogin").d("Saved JWT is not expired, do not override local.vdf")
                            return
                        }
                    } catch (e: Exception) {
                        Timber.tag("SteamTokenLogin").d("An unexpected error occurred: ${e.message}")
                    }
                }
            }

            Timber.tag("SteamTokenLogin").d("Overriding local.vdf")
            Files.write(localSteamDir.resolve("local.vdf"), createLocalVdf().toByteArray())
            clearCachedClientState()
            killWineServer()
            FileUtils.chmod(File(localSteamDir.toFile(), "local.vdf"), 505)
        } catch (e: Exception) {
            Timber.tag("SteamTokenLogin").d("An unexpected error occurred: ${e.message}")
            e.printStackTrace()
        }
    }
}
