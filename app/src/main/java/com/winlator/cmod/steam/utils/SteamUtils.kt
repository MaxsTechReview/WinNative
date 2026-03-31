package com.winlator.cmod.steam.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.winlator.cmod.container.Container
import com.winlator.cmod.core.WineRegistryEditor
import com.winlator.cmod.steam.data.DepotInfo
import com.winlator.cmod.steam.enums.PathType
import com.winlator.cmod.steam.enums.SpecialGameSaveMapping
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.service.SteamService.Companion.getAppDirName
import com.winlator.cmod.steam.service.SteamService.Companion.getAppInfoOf
import com.winlator.cmod.steam.utils.FileUtils
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.xenvironment.ImageFs
import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.enums.EPersonaState
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

object SteamUtils {
    /**
     * Writes the ColdClientLoader.ini file for the Goldberg emulator.
     */
    @JvmStatic
    fun writeColdClientIni(steamAppId: Int, container: Container) {
        val gameName = getAppDirName(getAppInfoOf(steamAppId))
        val executablePath = container.executablePath.replace("/", "\\")
        val exePath = "steamapps\\common\\$gameName\\$executablePath"
        val exeRunDir = "steamapps\\common\\$gameName"
        val exeCommandLine = container.execArgs
        val iniFile = File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/ColdClientLoader.ini")
        iniFile.parentFile?.mkdirs()

        val injectionSection = """
                [Injection]
                IgnoreLoaderArchDifference=1
                DllsToInjectFolder=extra_dlls
            """

        iniFile.writeText(
            """
                [SteamClient]

                Exe=$exePath
                ExeRunDir=$exeRunDir
                ExeCommandLine=$exeCommandLine
                AppId=$steamAppId

                # path to the steamclient dlls, both must be set, absolute paths or relative to the loader directory
                SteamClientDll=steamclient.dll
                SteamClient64Dll=steamclient64.dll

                $injectionSection
            """.trimIndent(),
        )
    }

    private fun coreSteamClientFiles(): Array<String> = arrayOf(
        "GameOverlayRenderer.dll",
        "GameOverlayRenderer64.dll",
        "steamclient.dll",
        "steamclient64.dll",
    )

    private fun steamClientFiles(): Array<String> = coreSteamClientFiles() + arrayOf(
        "steamclient_loader_x32.exe",
        "steamclient_loader_x64.exe",
    )

    @JvmStatic
    fun generateInterfacesFile(dllFile: File) {
        val outFile = File(dllFile.parent, "steam_interfaces.txt")
        if (outFile.exists()) return

        try {
            val bytes = Files.readAllBytes(dllFile.toPath())
            val strings = mutableSetOf<String>()

            val sb = StringBuilder()
            fun flush() {
                if (sb.length >= 10) {
                    val candidate = sb.toString()
                    if (candidate.matches(Regex("^Steam[A-Za-z]+[0-9]{3}\$", RegexOption.IGNORE_CASE)))
                        strings += candidate
                }
                sb.setLength(0)
            }

            for (b in bytes) {
                val ch = b.toInt() and 0xFF
                if (ch in 0x20..0x7E) {
                    sb.append(ch.toChar())
                } else {
                    flush()
                }
            }
            flush()

            if (strings.isEmpty()) {
                Timber.w("No Steam interface strings found in \${dllFile.name}")
                return
            }

            val sorted = strings.sorted()
            Files.write(outFile.toPath(), sorted)
            Timber.i("Generated steam_interfaces.txt (\${sorted.size} interfaces) for \${dllFile.name}")
        } catch (e: Exception) {
            Timber.w(e, "Failed to generate steam_interfaces.txt from native scanning for \${dllFile.name}")
        }
    }

    /**
     * Backup steamclient artifacts before any DRM/Steamless manipulation.
     */
    @JvmStatic
    @JvmOverloads
    fun backupSteamclientFiles(context: Context, steamAppId: Int = -1) {
        val imageFs = ImageFs.find(context)
        var backupCount = 0
        val backupDir = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam/steamclient_backup")
        backupDir.mkdirs()

        coreSteamClientFiles().forEach { file ->
            val dll = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam/$file")
            if (dll.exists()) {
                Files.copy(dll.toPath(), File(backupDir, "$file.orig").toPath(), StandardCopyOption.REPLACE_EXISTING)
                backupCount++
            }
        }

        val idLog = if (steamAppId >= 0) steamAppId.toString() else "unknown"
        Timber.i("backupSteamclientFiles complete (appId=$idLog, count=$backupCount)")
    }

    /**
     * Restore steamclient artifacts and clean injected extras if needed.
     */
    @JvmStatic
    @JvmOverloads
    fun restoreSteamclientFiles(context: Context, steamAppId: Int = -1) {
        val imageFs = ImageFs.find(context)
        var restoredCount = 0

        val origDir = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam")
        val backupDir = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam/steamclient_backup")
        if (backupDir.exists()) {
            coreSteamClientFiles().forEach { file ->
                val dll = File(backupDir, "$file.orig")
                if (dll.exists()) {
                    Files.copy(dll.toPath(), File(origDir, file).toPath(), StandardCopyOption.REPLACE_EXISTING)
                    restoredCount++
                }
            }

            arrayOf("steamclient_loader_x32.exe.orig", "steamclient_loader_x64.exe.orig").forEach { loaderBackup ->
                val staleBackup = File(backupDir, loaderBackup)
                if (staleBackup.exists()) {
                    staleBackup.delete()
                }
            }
        }

        val extraDllDir = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam/extra_dlls")
        if (extraDllDir.exists()) {
            extraDllDir.deleteRecursively()
        }

        arrayOf("steamclient_loader_x32.exe", "steamclient_loader_x64.exe").forEach { loaderExe ->
            val staleLoader = File(origDir, loaderExe)
            if (staleLoader.exists()) {
                staleLoader.delete()
            }
        }

        val idLog = if (steamAppId >= 0) steamAppId.toString() else "unknown"
        Timber.i("restoreSteamclientFiles complete (appId=$idLog, restored=$restoredCount)")
    }

    private val bootstrapClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .build()

    private val doh: DnsOverHttps = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://dns.google/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("8.8.4.4"),
        )
        .build()

    private val fallbackDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                doh.lookup(hostname)
            } catch (e: Exception) {
                Timber.w(e, "DoH lookup failed for \$hostname, falling back to system DNS")
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(fallbackDns)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .pingInterval(30, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .build()
    }

    @SuppressLint("HardwareIds")
    fun getMachineName(context: Context): String {
        val deviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return (deviceName ?: "Android Device") + " ($androidId)"
    }

    fun getPersonaState(state: Int): EPersonaState {
        return EPersonaState.from(state) ?: EPersonaState.Offline
    }

    fun getOSType(): EOSType {
        return EOSType.WinUnknown
    }

    @JvmStatic
    fun getSteamId64(): Long = PrefManager.steamUserSteamId64

    @JvmStatic
    fun getSteam3AccountId(): Int = PrefManager.steamUserAccountId

    fun getLanguage(steamLanguage: String): String {
        return steamLanguage.lowercase()
    }

    fun removeSpecialChars(s: String): String = s.replace(Regex("[^\\u0000-\\u007F]"), "")

    fun getUniqueDeviceId(context: Context): Int {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).hashCode()
    }

    /**
     * Sets up auto-login configuration in the Wine prefix for Steam.
     * Writes loginusers.vdf and sets Wine registry keys.
     */
    @JvmStatic
    fun autoLoginUserChanges(imageFs: ImageFs) {
        val vdfFileText = SteamService.getLoginUsersVdfOauth(
            steamId64 = SteamService.userSteamId?.convertToUInt64().toString(),
            account = PrefManager.username,
            refreshToken = PrefManager.refreshToken,
            accessToken = PrefManager.accessToken,
            personaName = SteamService.instance?.localPersona?.value?.name ?: PrefManager.username
        )
        val steamConfigDir = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam/config")
        try {
            steamConfigDir.mkdirs()
            File(steamConfigDir, "loginusers.vdf").writeText(vdfFileText)
            val rootDir = imageFs.rootDir
            val userRegFile = File(rootDir, ImageFs.WINEPREFIX + "/user.reg")
            
            val steamRoot = "C:\\Program Files (x86)\\Steam"
            val steamExe = "$steamRoot\\steam.exe"

            val hkcu = "Software\\Valve\\Steam"
            WineRegistryEditor(userRegFile).use { reg ->
                reg.setStringValue(hkcu, "AutoLoginUser", PrefManager.username)
                reg.setStringValue(hkcu, "SteamExe", steamExe)
                reg.setStringValue(hkcu, "SteamPath", steamRoot)
                reg.setStringValue(hkcu, "InstallPath", steamRoot)
            }
        } catch (e: Exception) {
            Timber.w("Could not add steam config options: $e")
        }
    }

    /**
     * Creates configuration files that make Steam run in lightweight mode
     * with reduced resource usage and disabled community features.
     */
    @JvmStatic
    fun setupLightweightSteamConfig(imageFs: ImageFs, steamId64: String?) {
        Timber.i("Setting up lightweight steam configs")
        try {
            val steamPath = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam")

            val userDataPath = File(steamPath, "userdata/$steamId64")
            val configPath = File(userDataPath, "config")
            val remotePath = File(userDataPath, "7/remote")

            configPath.mkdirs()
            remotePath.mkdirs()

            val localConfigContent = """
                "UserLocalConfigStore"
                {
                  "Software"
                  {
                    "Valve"
                    {
                      "Steam"
                      {
                        "SmallMode"                      "1"
                        "LibraryDisableCommunityContent" "1"
                        "LibraryLowBandwidthMode"        "1"
                        "LibraryLowPerfMode"             "1"
                      }
                    }
                  }
                  "friends"
                  {
                    "SignIntoFriends" "0"
                  }
                }
            """.trimIndent()

            val sharedConfigContent = """
                "UserRoamingConfigStore"
                {
                  "Software"
                  {
                    "Valve"
                    {
                      "Steam"
                      {
                        "SteamDefaultDialog" "#app_games"
                        "FriendsUI"
                        {
                          "FriendsUIJSON" "{\"bSignIntoFriends\":false,\"bAnimatedAvatars\":false,\"PersonaNotifications\":0,\"bDisableRoomEffects\":true}"
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val localConfigFile = File(configPath, "localconfig.vdf")
            val sharedConfigFile = File(remotePath, "sharedconfig.vdf")

            if (!localConfigFile.exists()) {
                localConfigFile.writeText(localConfigContent)
                Timber.i("Created lightweight Steam localconfig.vdf")
            }

            if (!sharedConfigFile.exists()) {
                sharedConfigFile.writeText(sharedConfigContent)
                Timber.i("Created lightweight Steam sharedconfig.vdf")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to setup lightweight Steam configuration")
        }
    }

    /**
     * Adds depots listing, achievements mapping, and save-location symlinks to steam_settings.
     * Mirrors GameNative's ensureSteamSettings extras for Goldberg/ColdClient.
     */
    @JvmStatic
    fun enrichSteamSettings(context: Context, appId: Int, settingsDir: File) {
        try {
            settingsDir.mkdirs()

            // depots.txt from installed depots (fall back to DLC depots)
            val depots = SteamService.getInstalledDepotsOf(appId)
                ?.takeIf { it.isNotEmpty() }
                ?: SteamService.getInstalledDlcDepotsOf(appId)

            val depotsFile = File(settingsDir, "depots.txt")
            if (depots != null && depots.isNotEmpty()) {
                depotsFile.writeText(depots.joinToString(System.lineSeparator()), Charsets.UTF_8)
            } else if (depotsFile.exists()) {
                depotsFile.delete()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed writing depots.txt for appId=$appId")
        }

        // Achievements mapping (best-effort; requires Steam login)
        runCatching {
            runBlocking {
                SteamService.generateAchievements(appId, settingsDir.absolutePath)
            }
        }.onFailure { e ->
            Timber.d(e, "Achievements generation skipped for appId=$appId")
        }

        // Special save-location symlinks
        ensureSaveLocationsForGames(context, appId)
    }

    /**
     * Ensures save location symlinks for special cases (mirrors GameNative).
     */
    @JvmStatic
    fun ensureSaveLocationsForGames(context: Context, steamAppId: Int) {
        val mappings = SpecialGameSaveMapping.registry + loadExtraSaveMappings(context)
        val mapping = mappings.find { it.appId == steamAppId } ?: return

        try {
            val accountId = SteamService.userSteamId?.accountID?.toLong()
                ?: PrefManager.steamUserAccountId.toLong()
            val steamId64 = SteamService.userSteamId?.convertToUInt64()?.toString()
                ?: PrefManager.steamUserSteamId64.toString()

            val basePath = mapping.pathType.toAbsPath(context, steamAppId, accountId)

            val sourceRelativePath = mapping.sourceRelativePath
                .replace("{64BitSteamID}", steamId64)
                .replace("{Steam3AccountID}", accountId.toString())
            val targetRelativePath = mapping.targetRelativePath
                .replace("{64BitSteamID}", steamId64)
                .replace("{Steam3AccountID}", accountId.toString())

            val sourcePath = File(basePath, sourceRelativePath)
            val targetPath = File(basePath, targetRelativePath)

            if (!sourcePath.exists()) {
                Timber.i("[${mapping.description}] Source save folder does not exist yet: ${sourcePath.absolutePath}")
                return
            }

            if (targetPath.exists()) {
                if (Files.isSymbolicLink(targetPath.toPath())) {
                    Timber.i("[${mapping.description}] Symlink already exists: ${targetPath.absolutePath}")
                } else {
                    Timber.w("[${mapping.description}] Target path exists but is not a symlink: ${targetPath.absolutePath}")
                }
                return
            }

            targetPath.parentFile?.mkdirs()
            Files.createSymbolicLink(targetPath.toPath(), sourcePath.toPath())
            Timber.i("[${mapping.description}] Created symlink: ${targetPath.absolutePath} -> ${sourcePath.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "[${mapping.description}] Failed to create save location symlink")
        }
    }

    private fun loadExtraSaveMappings(context: Context): List<SpecialGameSaveMapping> {
        val merged = mutableListOf<SpecialGameSaveMapping>()
        // Bundled asset overrides (optional)
        runCatching {
            context.assets.open("steam_save_mappings.json").use { input ->
                merged.addAll(parseMappingsJson(input.readBytes().toString(Charsets.UTF_8)))
            }
        }.onFailure { /* optional asset; ignore */ }

        // User-provided runtime file
        runCatching {
            val file = File(context.filesDir, "steam_save_mappings.json")
            if (file.exists()) {
                merged.addAll(parseMappingsJson(file.readText()))
            }
        }.onFailure { Timber.w(it, "Failed to load user steam_save_mappings.json") }

        // De-dup by appId/pathType/paths
        return merged.distinctBy { Triple(it.appId, it.pathType, it.sourceRelativePath + "|" + it.targetRelativePath) }
    }

    private fun parseMappingsJson(json: String): List<SpecialGameSaveMapping> {
        return runCatching {
            val arr = JSONArray(json)
            val result = mutableListOf<SpecialGameSaveMapping>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val appId = obj.optInt("appId", -1)
                val pathType = PathType.from(obj.optString("pathType", ""))
                val source = obj.optString("sourceRelativePath", "")
                val target = obj.optString("targetRelativePath", "")
                val description = obj.optString("description", "Custom Mapping")
                if (appId > 0 && pathType != PathType.None && source.isNotEmpty() && target.isNotEmpty()) {
                    result.add(
                        SpecialGameSaveMapping(
                            appId = appId,
                            pathType = pathType,
                            sourceRelativePath = source,
                            targetRelativePath = target,
                            description = description,
                        )
                    )
                }
            }
            result
        }.getOrElse {
            Timber.w(it, "Failed to parse save mappings JSON")
            emptyList()
        }
    }

    /**
     * Restores .original.exe backups created during Steamless unpack attempts.
     */
    @JvmStatic
    fun restoreOriginalExecutable(context: Context, steamAppId: Int) {
        Timber.i("Starting restoreOriginalExecutable for appId: $steamAppId")
        var restoredCount = 0

        val imageFs = ImageFs.find(context)
        val dosDevicesPath = File(imageFs.wineprefix, "dosdevices/a:")

        dosDevicesPath.walkTopDown().maxDepth(10)
            .filter { it.isFile && it.name.endsWith(".original.exe", ignoreCase = true) }
            .forEach { file ->
                try {
                    val origPath = file.toPath()
                    val originalPath = origPath.parent.resolve(origPath.fileName.toString().removeSuffix(".original.exe"))

                    if (Files.exists(originalPath)) {
                        Files.delete(originalPath)
                    }

                    Files.copy(origPath, originalPath, StandardCopyOption.REPLACE_EXISTING)
                    Timber.i("Restored ${originalPath.fileName} from backup")
                    restoredCount++
                } catch (e: Exception) {
                    Timber.w(e, "Failed to restore ${file.name} from backup")
                }
            }

        Timber.i("Finished restoreOriginalExecutable for appId: $steamAppId. Restored $restoredCount executable(s)")
    }

    /**
     * Restores the unpacked executable (.unpacked.exe) if it exists and is different from current .exe
     * This ensures we use the DRM-free version when not using real Steam
     */
    @JvmStatic
    fun restoreUnpackedExecutable(context: Context, steamAppId: Int) {
        try {
            val imageFs = ImageFs.find(context)
            val appDirPath = com.winlator.cmod.steam.service.SteamService.getAppDirPath(steamAppId)

            val container = com.winlator.cmod.steam.utils.ContainerUtils.getContainer(context, "STEAM_$steamAppId")
            val executablePath = container?.executablePath ?: ""
            val drives = container?.drives ?: ""

            // Find the drive letter for the game directory using proper drives iterator
            var drive = 'A' // default to A: drive
            for (driveEntry in Container.drivesIterator(drives)) {
                if (driveEntry[1] == appDirPath) {
                    drive = driveEntry[0][0]
                    break
                }
            }
            if (drive == 'A' && !drives.contains("A:")) {
                Timber.w("Could not locate game drive for appDirPath=$appDirPath in drives, defaulting to A:")
            }
            val executableFile = "$drive:\\${executablePath}"

            val exe = File(imageFs.wineprefix + "/dosdevices/" + executableFile.replace("A:", "a:").replace('\\', '/'))
            val unpackedExe = File(imageFs.wineprefix + "/dosdevices/" + executableFile.replace("A:", "a:").replace('\\', '/') + ".unpacked.exe")

            if (unpackedExe.exists()) {
                val areFilesDifferent = !exe.exists() ||
                    exe.length() != unpackedExe.length() ||
                    exe.lastModified() != unpackedExe.lastModified()

                if (areFilesDifferent) {
                    Files.copy(unpackedExe.toPath(), exe.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    Timber.i("Restored unpacked executable from ${unpackedExe.name} to ${exe.name}")
                } else {
                    Timber.i("Unpacked executable is already current, no restore needed")
                }
            } else {
                Timber.i("No unpacked executable found, using current executable")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore unpacked executable for appId $steamAppId")
        }
    }


    /**
     * Sync achievements/stats generated by Goldberg back to Steam (best-effort).
     */
    @JvmStatic
    fun syncGoldbergAchievementsAndStats(context: Context, appId: Int) {
        if (!SteamService.isLoggedIn) {
            queuePendingGoldbergSync(context, appId)
            Timber.w("Goldberg sync queued because Steam is not logged in for appId=$appId")
            return
        }
        runBlocking {
            runCatching { SteamService.syncAchievementsFromGoldberg(appId) }
                .onFailure {
                    Timber.w(it, "Goldberg achievement sync failed for appId=$appId; queuing for retry")
                    queuePendingGoldbergSync(context, appId)
                }
        }
    }

    @JvmStatic
    fun processPendingGoldbergSyncs(context: Context) {
        if (!SteamService.isLoggedIn) return
        val file = File(context.filesDir, "pending_goldberg_syncs.json")
        if (!file.exists()) return
        val pending = runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { arr.optInt(it, -1).takeIf { id -> id > 0 } }.toSet()
        }.getOrElse {
            Timber.w(it, "Failed to read pending Goldberg syncs")
            emptySet()
        }
        if (pending.isEmpty()) return
        val remaining = mutableSetOf<Int>()
        pending.forEach { id ->
            try {
                syncGoldbergAchievementsAndStats(context, id)
            } catch (e: Exception) {
                Timber.w(e, "Pending Goldberg sync failed for appId=$id; will keep queued")
                remaining.add(id)
            }
        }
        if (remaining.isEmpty()) {
            file.delete()
        } else {
            file.writeText(JSONArray(remaining.toList()).toString(), Charsets.UTF_8)
        }
    }

    private fun queuePendingGoldbergSync(context: Context, appId: Int) {
        runCatching {
            val file = File(context.filesDir, "pending_goldberg_syncs.json")
            val set = if (file.exists()) {
                val arr = JSONArray(file.readText())
                (0 until arr.length()).mapNotNull { arr.optInt(it, -1).takeIf { id -> id > 0 } }.toMutableSet()
            } else mutableSetOf()
            set.add(appId)
            file.writeText(JSONArray(set.toList()).toString(), Charsets.UTF_8)
        }.onFailure { Timber.w(it, "Failed to queue pending Goldberg sync for appId=$appId") }
    }

    /**
     * Creates a Steam ACF (Application Cache File) manifest for the given app.
     * This allows real Steam to detect the game as installed.
     */
    @JvmStatic
    fun createAppManifest(context: Context, steamAppId: Int) {
        try {
            Timber.i("Attempting to createAppManifest for appId: $steamAppId")
            val appInfo = getAppInfoOf(steamAppId) ?: run {
                Timber.w("No app info found for appId: $steamAppId")
                return
            }

            val imageFs = ImageFs.find(context)

            val steamappsDir = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam/steamapps")
            steamappsDir.mkdirs()

            val commonDir = File(steamappsDir, "common")
            commonDir.mkdirs()

            val gameDir = File(SteamService.getAppDirPath(steamAppId))
            val gameName = gameDir.name
            val sizeOnDisk = calculateDirectorySize(gameDir)

            // Create symlink from Steam common directory to actual game directory
            val steamGameLink = File(commonDir, gameName)
            if (!steamGameLink.exists()) {
                try {
                    Files.createSymbolicLink(steamGameLink.toPath(), gameDir.toPath())
                    Timber.i("Created symlink from ${steamGameLink.absolutePath} to ${gameDir.absolutePath}")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to create symlink, trying copy instead")
                }
            }

            val buildId = appInfo.branches["public"]?.buildId ?: 0L
            val downloadableDepots = SteamService.getDownloadableDepots(steamAppId)

            val regularDepots = mutableMapOf<Int, DepotInfo>()
            val sharedDepots = mutableMapOf<Int, DepotInfo>()

            downloadableDepots.forEach { (depotId, depotInfo) ->
                val manifest = depotInfo.manifests["public"]
                if (manifest != null && manifest.gid != 0L) {
                    regularDepots[depotId] = depotInfo
                } else {
                    sharedDepots[depotId] = depotInfo
                }
            }

            val acfContent = buildString {
                appendLine("\"AppState\"")
                appendLine("{")
                appendLine("\t\"appid\"\t\t\"$steamAppId\"")
                appendLine("\t\"Universe\"\t\t\"1\"")
                appendLine("\t\"name\"\t\t\"${escapeString(appInfo.name)}\"")
                appendLine("\t\"StateFlags\"\t\t\"4\"")
                appendLine("\t\"LastUpdated\"\t\t\"${System.currentTimeMillis() / 1000}\"")
                appendLine("\t\"SizeOnDisk\"\t\t\"$sizeOnDisk\"")
                appendLine("\t\"buildid\"\t\t\"$buildId\"")

                val actualInstallDir = appInfo.config.installDir.ifEmpty { gameName }
                appendLine("\t\"installdir\"\t\t\"${escapeString(actualInstallDir)}\"")

                appendLine("\t\"LastOwner\"\t\t\"0\"")
                appendLine("\t\"BytesToDownload\"\t\t\"0\"")
                appendLine("\t\"BytesDownloaded\"\t\t\"0\"")
                appendLine("\t\"AutoUpdateBehavior\"\t\t\"0\"")
                appendLine("\t\"AllowOtherDownloadsWhileRunning\"\t\t\"0\"")
                appendLine("\t\"ScheduledAutoUpdate\"\t\t\"0\"")

                if (regularDepots.isNotEmpty()) {
                    appendLine("\t\"InstalledDepots\"")
                    appendLine("\t{")
                    regularDepots.forEach { (depotId, depotInfo) ->
                        val manifest = depotInfo.manifests["public"]
                        appendLine("\t\t\"$depotId\"")
                        appendLine("\t\t{")
                        appendLine("\t\t\t\"manifest\"\t\t\"${manifest?.gid ?: "0"}\"")
                        appendLine("\t\t\t\"size\"\t\t\"${manifest?.size ?: 0}\"")
                        appendLine("\t\t}")
                    }
                    appendLine("\t}")
                }

                appendLine("\t\"UserConfig\" { \"language\" \"english\" }")
                appendLine("\t\"MountedConfig\" { \"language\" \"english\" }")
                appendLine("}")
            }

            val acfFile = File(steamappsDir, "appmanifest_$steamAppId.acf")
            acfFile.writeText(acfContent)
            Timber.i("Created ACF manifest for ${appInfo.name} at ${acfFile.absolutePath}")

            if (sharedDepots.isNotEmpty()) {
                val steamworksAcfContent = buildString {
                    appendLine("\"AppState\"")
                    appendLine("{")
                    appendLine("\t\"appid\"\t\t\"228980\"")
                    appendLine("\t\"Universe\"\t\t\"1\"")
                    appendLine("\t\"name\"\t\t\"Steamworks Common Redistributables\"")
                    appendLine("\t\"StateFlags\"\t\t\"4\"")
                    appendLine("\t\"installdir\"\t\t\"Steamworks Shared\"")
                    appendLine("\t\"buildid\"\t\t\"1\"")
                    appendLine("\t\"BytesToDownload\"\t\t\"0\"")
                    appendLine("\t\"BytesDownloaded\"\t\t\"0\"")
                    appendLine("}")
                }

                val steamworksAcfFile = File(steamappsDir, "appmanifest_228980.acf")
                steamworksAcfFile.writeText(steamworksAcfContent)
                Timber.i("Created Steamworks Common Redistributables ACF manifest")
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to create ACF manifest for appId $steamAppId")
        }
    }

    /**
     * Writes steam.cfg to skip Steam bootstrapper self-update and marks all common
     * redistributables (DirectX, .NET, XNA, OpenAL) as already installed in system.reg,
     * preventing Steam from re-running their setup wizards on first launch.
     * Mirrors GameNative's skipFirstTimeSteamSetup() fully.
     */
    @JvmStatic
    fun skipFirstTimeSteamSetup(rootDir: File) {
        // Write steam.cfg
        try {
            val steamDir = File(rootDir, ImageFs.WINEPREFIX + "/drive_c/Program Files (x86)/Steam")
            steamDir.mkdirs()
            val cfgFile = File(steamDir, "steam.cfg")
            if (!cfgFile.exists()) {
                cfgFile.writeText("BootStrapperInhibitAll=Enable\nBootStrapperForceSelfUpdate=False")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to write steam.cfg")
        }

        // Mark redistributables as installed in system.reg so Steam skips setup wizards
        val redistributables = listOf(
            "DirectX\\Jun2010" to "DXSetup",
            ".NET\\3.5" to "3.5 SP1",
            ".NET\\3.5 Client Profile" to "3.5 Client Profile SP1",
            ".NET\\4.0" to "4.0",
            ".NET\\4.0 Client Profile" to "4.0 Client Profile",
            ".NET\\4.5.2" to "4.5.2",
            ".NET\\4.6" to "4.6",
            ".NET\\4.7" to "4.7",
            ".NET\\4.8" to "4.8",
            "XNA\\3.0" to "3.0",
            "XNA\\3.1" to "3.1",
            "XNA\\4.0" to "4.0",
            "OpenAL\\2.0.7.0" to "2.0.7.0",
            ".NET\\4.5.1" to "4.5.1",
            ".NET\\4.6.1" to "4.6.1",
            ".NET\\4.6.2" to "4.6.2",
            ".NET\\4.7.1" to "4.7.1",
            ".NET\\4.7.2" to "4.7.2",
            ".NET\\4.8.1" to "4.8.1",
        )
        val systemRegFile = File(rootDir, ImageFs.WINEPREFIX + "/system.reg")
        if (!systemRegFile.exists()) return
        try {
            WineRegistryEditor(systemRegFile).use { reg ->
                redistributables.forEach { (subPath, valueName) ->
                    reg.setDwordValue("Software\\Valve\\Steam\\Apps\\CommonRedist\\$subPath", valueName, 1)
                    reg.setDwordValue("Software\\Wow6432Node\\Valve\\Steam\\Apps\\CommonRedist\\$subPath", valueName, 1)
                }
            }
            Timber.i("skipFirstTimeSteamSetup: marked ${redistributables.size} redistributables as installed")
        } catch (e: Exception) {
            Timber.w(e, "Failed to set redistributable registry entries")
        }
    }

    /**
     * Generates the cloud save config sections for configs.app.ini.
     * Produces [app::cloud_save::general] and, when Windows save patterns exist,
     * [app::cloud_save::win] with dir1=, dir2=, ... entries — matching GameNative exactly.
     */
    @JvmStatic
    fun generateCloudSaveConfig(steamAppId: Int): String {
        val steamApp = getAppInfoOf(steamAppId) ?: return ""
        val windowsPatterns = steamApp.ufs.saveFilePatterns.filter { it.root.isWindows }

        return buildString {
            if (windowsPatterns.isNotEmpty()) {
                appendLine("[app::cloud_save::general]")
                appendLine("create_default_dir=1")
                appendLine("create_specific_dirs=1")
                appendLine()
                appendLine("[app::cloud_save::win]")
                val uniqueDirs = LinkedHashSet<String>()
                windowsPatterns.forEach { pattern ->
                    val root = if (pattern.root.name == "GameInstall") "gameinstall" else pattern.root.name
                    val path = pattern.path
                        .replace("{64BitSteamID}", "{::64BitSteamID::}")
                        .replace("{Steam3AccountID}", "{::Steam3AccountID::}")
                    uniqueDirs.add("{::$root::}/$path")
                }
                uniqueDirs.forEachIndexed { index, dir ->
                    appendLine("dir${index + 1}=$dir")
                }
            }
        }
    }

    private fun escapeString(input: String?): String {
        if (input == null) return ""
        return input.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }

    private fun calculateDirectorySize(directory: File): Long {
        if (!directory.exists() || !directory.isDirectory) {
            return 0L
        }
        var size = 0L
        try {
            directory.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error calculating directory size")
        }
        return size
    }

    /**
     * Writes the complete steam_settings directory next to a DLL (steam_api.dll or steamclient.dll).
     * Mirrors GameNative's ensureSteamSettings() — single source of truth for all Goldberg config.
     *
     * Writes:
     *  - steam_appid.txt (parent dir level)
     *  - steam_settings/steam_appid.txt
     *  - steam_settings/depots.txt
     *  - steam_settings/configs.user.ini  ([user::general] + conditional [user::saves])
     *  - steam_settings/configs.app.ini   ([app::dlcs] with real IDs, [app::cloud_save::general/win])
     *  - steam_settings/configs.main.ini  ([main::connectivity] with offline support)
     *  - steam_settings/controller/       (Steam Input VDF config if useSteamInput=true)
     *  - steam_settings/supported_languages.txt
     *
     * @param settingsParentDir  directory containing the DLL (steam_api.dll or steamclient.dll)
     * @param appId              Steam app ID
     * @param language           container language (e.g. "english")
     * @param isOffline          whether to write offline=1 in configs.main.ini
     * @param forceDlc           whether to write unlock_all=1 regardless of installed DLC
     * @param useSteamInput      whether to generate Steam Input controller config
     * @param ticketBase64       encrypted app ticket for online auth (may be null)
     */
    @JvmStatic
    @JvmOverloads
    fun writeCompleteSettingsDir(
        settingsParentDir: File,
        appId: Int,
        language: String = "english",
        isOffline: Boolean = false,
        forceDlc: Boolean = false,
        useSteamInput: Boolean = false,
        ticketBase64: String? = null,
    ) {
        try {
            settingsParentDir.mkdirs()

            // steam_appid.txt at parent level
            val appIdFileUpper = File(settingsParentDir, "steam_appid.txt")
            if (!appIdFileUpper.exists()) {
                appIdFileUpper.writeText(appId.toString())
            }

            val settingsDir = File(settingsParentDir, "steam_settings")
            settingsDir.mkdirs()

            // steam_settings/steam_appid.txt
            File(settingsDir, "steam_appid.txt").writeText(appId.toString())

            // steam_settings/depots.txt — from installed depots, fall back to DLC depots
            val depots = SteamService.getInstalledDepotsOf(appId)
                ?.takeIf { it.isNotEmpty() }
                ?: SteamService.getInstalledDlcDepotsOf(appId)
            val depotsFile = File(settingsDir, "depots.txt")
            if (depots != null && depots.isNotEmpty()) {
                depotsFile.writeText(depots.sorted().joinToString(System.lineSeparator()))
            } else {
                depotsFile.delete()
            }

            // --- configs.user.ini ---
            val accountName = PrefManager.username.takeIf { it.isNotEmpty() } ?: "Player"
            val accountSteamId = SteamService.userSteamId?.convertToUInt64()?.toString()
                ?: PrefManager.steamUserSteamId64.takeIf { it != 0L }?.toString()
                ?: "0"
            val accountId = SteamService.userSteamId?.accountID?.toLong()
                ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                ?: 0L

            val appInfo = getAppInfoOf(appId)
            val hasSaveFilePatterns = appInfo?.ufs?.saveFilePatterns?.isNotEmpty() == true

            val userIniContent = buildString {
                appendLine("[user::general]")
                appendLine("account_name=$accountName")
                appendLine("account_steamid=$accountSteamId")
                appendLine("language=${language.lowercase()}")
                if (!ticketBase64.isNullOrEmpty()) {
                    appendLine("ticket=$ticketBase64")
                }
                // Only add [user::saves] when the game has no Cloud save patterns
                if (!hasSaveFilePatterns) {
                    val steamUserDataPath = "C:\\Program Files (x86)\\Steam\\userdata\\$accountId"
                    appendLine()
                    appendLine("[user::saves]")
                    appendLine("local_save_path=$steamUserDataPath")
                }
            }
            File(settingsDir, "configs.user.ini").writeText(userIniContent)

            // --- configs.app.ini ---
            val dlcIds = SteamService.getInstalledDlcDepotsOf(appId)
            val dlcApps = SteamService.getDownloadableDlcAppsOf(appId)
            val hiddenDlcApps = SteamService.getHiddenDlcAppsOf(appId)
            val appendedDlcIds = mutableListOf<Int>()

            val appIniContent = buildString {
                appendLine("[app::dlcs]")
                appendLine("unlock_all=${if (forceDlc) 1 else 0}")
                dlcIds?.sorted()?.forEach {
                    appendLine("$it=dlc$it")
                    appendedDlcIds.add(it)
                }
                dlcApps?.forEach { dlcApp ->
                    val installedDlcApp = SteamService.getInstalledApp(dlcApp.id)
                    if (installedDlcApp != null && !appendedDlcIds.contains(dlcApp.id)) {
                        appendLine("${dlcApp.id}=dlc${dlcApp.id}")
                        appendedDlcIds.add(dlcApp.id)
                    }
                }
                hiddenDlcApps?.forEach { hiddenDlcApp ->
                    if (!appendedDlcIds.contains(hiddenDlcApp.id) &&
                        (appInfo?.depots?.filter { (_, depot) -> depot.dlcAppId == hiddenDlcApp.id }?.size ?: 0) <= 1) {
                        appendLine("${hiddenDlcApp.id}=dlc${hiddenDlcApp.id}")
                    }
                }
                if (appInfo != null) {
                    appendLine()
                    append(generateCloudSaveConfig(appId))
                }
            }
            File(settingsDir, "configs.app.ini").writeText(appIniContent)

            // --- configs.main.ini ---
            val mainIniContent = buildString {
                appendLine("[main::connectivity]")
                appendLine("disable_lan_only=${if (isOffline) 0 else 1}")
                if (isOffline) {
                    appendLine("offline=1")
                }
                appendLine()
                appendLine("[main::stats]")
                appendLine("allow_unknown_stats=1")
            }
            File(settingsDir, "configs.main.ini").writeText(mainIniContent)

            // --- controller config ---
            val controllerDir = File(settingsDir, "controller")
            if (useSteamInput) {
                val controllerVdfText = SteamService.resolveSteamControllerVdfText(appId)
                if (!controllerVdfText.isNullOrEmpty()) {
                    runCatching {
                        SteamControllerVdfUtils.generateControllerConfig(
                            controllerVdfText,
                            controllerDir.toPath(),
                        )
                    }.onFailure { e ->
                        Timber.w(e, "Failed to generate controller config for appId=$appId")
                    }
                }
            } else {
                runCatching {
                    if (controllerDir.exists()) controllerDir.deleteRecursively()
                }.onFailure { e ->
                    Timber.w(e, "Failed to delete controller config dir for appId=$appId")
                }
            }

            // --- supported_languages.txt ---
            val supportedLanguages = listOf(
                "arabic", "bulgarian", "schinese", "tchinese", "czech", "danish", "dutch",
                "english", "finnish", "french", "german", "greek", "hungarian", "italian",
                "japanese", "koreana", "norwegian", "polish", "portuguese", "brazilian",
                "romanian", "russian", "spanish", "latam", "swedish", "thai", "turkish",
                "ukrainian", "vietnamese",
            )
            File(settingsDir, "supported_languages.txt").writeText(supportedLanguages.joinToString("\n"))

            Timber.i("writeCompleteSettingsDir complete for appId=$appId in ${settingsParentDir.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to write complete steam_settings dir for appId=$appId")
        }
    }

    /**
     * Restores all steam_api.dll.orig / steam_api64.dll.orig backups in the app directory
     * back to their original names (steam_api.dll / steam_api64.dll).
     * Called before real-Steam launch and during ColdClient setup.
     */
    @JvmStatic
    fun putBackSteamDlls(appDirPath: String) {
        val rootPath = Paths.get(appDirPath)

        rootPath.toFile().walkTopDown().maxDepth(10).forEach { file ->
            val path = file.toPath()
            if (!file.isFile) return@forEach
            val name = path.fileName.toString()
            if (!name.startsWith("steam_api", ignoreCase = true)) return@forEach
            if (!name.endsWith(".orig", ignoreCase = true)) return@forEach

            val is64Bit = name.equals("steam_api64.dll.orig", ignoreCase = true)
            val is32Bit = name.equals("steam_api.dll.orig", ignoreCase = true)
            if (!is32Bit && !is64Bit) return@forEach

            try {
                val dllName = if (is64Bit) "steam_api64.dll" else "steam_api.dll"
                val originalPath = path.parent.resolve(dllName)
                Timber.i("Found ${path.fileName} at ${path.toAbsolutePath()}, restoring...")

                if (Files.exists(originalPath)) {
                    Files.delete(originalPath)
                }
                Files.copy(path, originalPath)
                Timber.i("Restored $dllName from .orig backup")
            } catch (e: IOException) {
                Timber.w(e, "Failed to restore ${file.name} from .orig backup")
            }
        }
    }

    /**
     * Updates localconfig.vdf with the container's LaunchOptions for the given appId,
     * and updates UserConfig/MountedConfig language in the ACF manifest.
     * Mirrors GameNative's updateOrModifyLocalConfig().
     */
    @JvmStatic
    fun updateOrModifyLocalConfig(imageFs: ImageFs, container: Container, appId: String, steamUserId64: String) {
        try {
            val exeCommandLine = container.execArgs

            val steamPath = File(imageFs.wineprefix, "drive_c/Program Files (x86)/Steam")
            val userDataPath = File(steamPath, "userdata/$steamUserId64")
            val configPath = File(userDataPath, "config")
            configPath.mkdirs()

            val localConfigFile = File(configPath, "localconfig.vdf")

            if (localConfigFile.exists()) {
                val vdfContent = FileUtils.readFileAsString(localConfigFile.absolutePath)
                if (vdfContent != null) {
                    val vdfData = KeyValue.loadFromString(vdfContent)
                    if (vdfData != null) {
                        val app = vdfData["Software"]["Valve"]["Steam"]["apps"][appId]
                        val option = app.children.firstOrNull { it.name == "LaunchOptions" }
                        if (option != null) {
                            option.value = exeCommandLine.orEmpty()
                        } else {
                            app.children.add(KeyValue("LaunchOptions", exeCommandLine))
                        }
                        vdfData.saveToFile(localConfigFile, false)
                    }
                }
            } else {
                val vdfData = KeyValue("UserLocalConfigStore")
                val option = KeyValue("LaunchOptions", exeCommandLine)
                val software = KeyValue("Software")
                val valve = KeyValue("Valve")
                val steam = KeyValue("Steam")
                val apps = KeyValue("apps")
                val app = KeyValue(appId)

                app.children.add(option)
                apps.children.add(app)
                steam.children.add(apps)
                valve.children.add(steam)
                software.children.add(valve)
                vdfData.children.add(software)

                vdfData.saveToFile(localConfigFile, false)
            }

            // Also update UserConfig + MountedConfig language in the ACF manifest
            val userLanguage = runCatching {
                container.getExtra("containerLanguage", null)
                    ?.takeIf { it.isNotEmpty() }
                    ?: "english"
            }.getOrDefault("english")

            val steamappsDir = File(steamPath, "steamapps")
            val appManifestFile = File(steamappsDir, "appmanifest_$appId.acf")

            if (appManifestFile.exists()) {
                val manifestContent = FileUtils.readFileAsString(appManifestFile.absolutePath)
                if (manifestContent != null) {
                    val manifestData = KeyValue.loadFromString(manifestContent)
                    if (manifestData != null) {
                        fun upsertLanguage(sectionName: String) {
                            val section = manifestData.children.firstOrNull { it.name == sectionName }
                            if (section != null) {
                                val langKey = section.children.firstOrNull { it.name == "language" }
                                if (langKey != null) langKey.value = userLanguage
                                else section.children.add(KeyValue("language", userLanguage))
                            } else {
                                val newSection = KeyValue(sectionName)
                                newSection.children.add(KeyValue("language", userLanguage))
                                manifestData.children.add(newSection)
                            }
                        }
                        upsertLanguage("UserConfig")
                        upsertLanguage("MountedConfig")
                        manifestData.saveToFile(appManifestFile, false)
                        Timber.i("Updated ACF manifest language to $userLanguage for appId $appId")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update or modify local config for appId $appId")
        }
    }

    /**
     * Queries PCGamingWiki's Cargo API for the highest Direct3D major version used by [steamAppId].
     * Invokes [callback] with the version number (9, 10, 11, or 12), or -1 on failure/unknown.
     */
    @JvmStatic
    fun fetchDirect3DMajor(steamAppId: Int, callback: (Int) -> Unit) {
        Timber.i("[DX Fetch] Starting fetchDirect3DMajor for appId=%d", steamAppId)
        val where = URLEncoder.encode("Infobox_game.Steam_AppID HOLDS \"$steamAppId\"", "UTF-8")
        val url = "https://pcgamingwiki.com/w/api.php" +
                "?action=cargoquery" +
                "&tables=Infobox_game,API" +
                "&join_on=Infobox_game._pageID=API._pageID" +
                "&fields=API.Direct3D_versions" +
                "&where=$where" +
                "&format=json"

        Timber.i("[DX Fetch] Query URL=%s", url)

        http.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.w(e, "[DX Fetch] Request failed for appId=%d", steamAppId)
                callback(-1)
            }

            override fun onResponse(call: Call, res: Response) {
                res.use {
                    try {
                        val body = it.body?.string() ?: run { callback(-1); return }
                        Timber.i("[DX Fetch] Raw body=%s", body)
                        val arr = JSONObject(body).optJSONArray("cargoquery")
                            ?: run { callback(-1); return }

                        val raw = arr.optJSONObject(0)
                            ?.optJSONObject("title")
                            ?.optString("Direct3D versions")
                            ?.trim() ?: ""

                        Timber.i("[DX Fetch] Direct3D versions string=%s", raw)

                        val dx = Regex("\\b(9|10|11|12)\\b")
                            .findAll(raw)
                            .map { m -> m.value.toInt() }
                            .maxOrNull() ?: -1

                        Timber.i("[DX Fetch] Resolved dx=%d for appId=%d", dx, steamAppId)
                        callback(dx)
                    } catch (e: Exception) {
                        Timber.w(e, "[DX Fetch] Parse error for appId=%d", steamAppId)
                        callback(-1)
                    }
                }
            }
        })
    }
}
