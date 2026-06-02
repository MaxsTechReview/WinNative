package com.winlator.cmod.feature.stores.steam.wnsteam

import android.content.Context
import android.util.Log
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.runtime.display.environment.ImageFs
import java.io.File

// JNI facade that boots libsteamclient.so for Wine's lsteamclient.dll.
object WnSteamBootstrap {

    private const val TAG = "WnSteamBootstrap"

    @Volatile private var initialized = false
    @Volatile private var prewarmRan = false

    init {
        try {
            System.loadLibrary("wnsteambootstrap")
        } catch (t: UnsatisfiedLinkError) {
            // Optional in some variants.
            Log.w(TAG, "libwnsteambootstrap.so not found in jniLibs: ${t.message}")
        }
    }

    /**
     * Initialize libsteamclient.so. Returns 0 on success, or a negative error.
     * [extraEnv] is applied before dlopen for module-init reads.
     */
    @Synchronized
    fun start(
        context: Context,
        libPath: String,
        home: String,
        steam3Master: String,
        steamClientService: String,
        extraEnv: Array<String>,
        accountName: String?,
        refreshToken: String?,
        steamId64: Long,
        appId: Int = 0,
    ): Int {
        if (initialized) {
            if (appId > 0) {
                Log.i(
                    TAG,
                    "start: app-bound launch requested while bootstrap is live; forcing a clean restart (appId=$appId)",
                )
                stop()
            } else {
                Log.i(TAG, "start: already initialized")
                return 0
            }
        }
        val rc = try {
            nativeInit(context, libPath, home, steam3Master, steamClientService,
                       extraEnv, accountName, refreshToken, steamId64, appId)
        } catch (t: UnsatisfiedLinkError) {
            Log.w(TAG, "nativeInit unavailable: ${t.message}")
            return -100
        }
        if (rc == 0) initialized = true
        Log.i(TAG, "start rc=$rc initialized=$initialized appId=$appId")
        return rc
    }

    fun prewarm(context: Context) {
        if (prewarmRan || initialized) return
        prewarmRan = true
        val app = context.applicationContext
        Thread({
            try {
                val account = PrefManager.username
                val token = PrefManager.refreshToken
                val steamId = PrefManager.steamUserSteamId64
                if (account.isEmpty() || token.isEmpty() || steamId <= 0L) {
                    Log.i(TAG, "prewarm: no Steam credentials yet; skipping")
                    prewarmRan = false   // permit a retry once signed in
                    return@Thread
                }
                if (!WnSteamAssetsInstaller.installBionicRuntime(app)) {
                    Log.w(TAG, "prewarm: libsteamclient.so staging failed")
                    prewarmRan = false
                    return@Thread
                }
                val imageFs = ImageFs.find(app)
                val libPath = File(imageFs.rootDir, "usr/lib/libsteamclient.so").absolutePath
                val home = File(imageFs.rootDir, "home").absolutePath
                val rc = start(
                    app, libPath, home,
                    "127.0.0.1:57343", "127.0.0.1:57344",
                    emptyArray(), account, token, steamId,
                    appId = 0,   // library/prewarm: no specific game bound
                )
                Log.i(TAG, "prewarm: bootstrap start rc=$rc (Steam client pre-warmed, appId=0)")
            } catch (t: Throwable) {
                Log.e(TAG, "prewarm failed", t)
                prewarmRan = false
            }
        }, "WnSteamPrewarm").start()
    }

    @Synchronized
    fun stop() {
        if (!initialized) return
        try { nativeShutdown() } catch (_: UnsatisfiedLinkError) {}
        initialized = false
        prewarmRan = false
        Log.i(TAG, "stop done")
    }

    // Pre-warm libsteamclient.so's PICS cache for the game and DLC.
    fun prepareApp(parentAppId: Int, dlcAppIds: IntArray) {
        if (!initialized) return
        val all = IntArray(1 + dlcAppIds.size).also {
            it[0] = parentAppId
            System.arraycopy(dlcAppIds, 0, it, 1, dlcAppIds.size)
        }
        try { nativePrepareApp(all) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setCloudEnabled(appId: Int, enabled: Boolean) {
        if (!initialized) return
        try { nativeSetCloudEnabled(appId, enabled) } catch (_: UnsatisfiedLinkError) {}
    }

    // Whether libsteamclient.so reports a logged-on user.
    fun isLoggedOn(): Boolean {
        if (!initialized) return false
        return try { nativeIsLoggedOn() } catch (_: UnsatisfiedLinkError) { false }
    }

    // SteamID64 from libsteamclient.so, or 0 when not logged on.
    fun steamId(): Long {
        if (!initialized) return 0
        return try { nativeGetSteamId() } catch (_: UnsatisfiedLinkError) { 0L }
    }

    @JvmStatic private external fun nativeInit(
        context: Context,
        libPath: String,
        home: String,
        steam3Master: String,
        steamClientService: String,
        extraEnv: Array<String>,
        accountName: String?,
        refreshToken: String?,
        steamId64: Long,
        appId: Int,
    ): Int
    @JvmStatic private external fun nativeShutdown()
    @JvmStatic private external fun nativePrepareApp(appIds: IntArray)
    @JvmStatic private external fun nativeSetCloudEnabled(appId: Int, enabled: Boolean)
    @JvmStatic private external fun nativeIsLoggedOn(): Boolean
    @JvmStatic private external fun nativeGetSteamId(): Long
    @JvmStatic private external fun nativeBIsSubscribedApp(appId: Int): Boolean
    @JvmStatic private external fun nativeISteamAppsBIsAppInstalled(appId: Int): Boolean
    @JvmStatic private external fun nativeISteamAppsGetAppInstallDir(appId: Int): String?
    @JvmStatic private external fun nativeISteamAppsGetInstalledDepots(appId: Int): IntArray
    @JvmStatic private external fun nativeISteamAppsGetCurrentGameLanguage(): String?
    @JvmStatic private external fun nativeISteamAppsBIsDlcInstalled(dlcAppId: Int): Boolean
    @JvmStatic private external fun nativeISteamAppsGetEarliestPurchaseUnixTime(appId: Int): Int
    @JvmStatic private external fun nativeISteamAppsGetDLCCount(appId: Int): Int
    @JvmStatic private external fun nativeISteamAppsGetAppOwner(): Long
    @JvmStatic private external fun nativeISteamAppsBIsSubscribedFromFamilySharing(): Boolean
    @JvmStatic private external fun nativeISteamAppsGetAppBuildId(): Int
    @JvmStatic private external fun nativeISteamUserBLoggedOn(): Boolean
    @JvmStatic private external fun nativeISteamUserHasLicenseForApp(steamId64: Long, appId: Int): Int
    @JvmStatic private external fun nativeISteamUserGetSteamID(): Long
    @JvmStatic private external fun nativeISteamUtilsGetAppID(): Int
    @JvmStatic private external fun nativeISteamUtilsGetServerRealTime(): Int
    @JvmStatic private external fun nativeISteamUtilsGetIPCountry(): String?
    @JvmStatic private external fun nativeISteamUtilsGetSteamUILanguage(): String?
    @JvmStatic private external fun nativeISteamUtilsGetCurrentBatteryPower(): Int
    @JvmStatic private external fun nativeISteamUtilsGetImageSize(imageHandle: Int): IntArray
    @JvmStatic private external fun nativeISteamUtilsGetImageRGBA(imageHandle: Int, outRgba: ByteArray): Boolean
    @JvmStatic private external fun nativeISteamRemoteStorageGetFileCount(): Int
    @JvmStatic private external fun nativeISteamRemoteStorageIsCloudEnabledForAccount(): Boolean
    @JvmStatic private external fun nativeISteamRemoteStorageIsCloudEnabledForApp(): Boolean
    @JvmStatic private external fun nativeISteamRemoteStorageGetQuota(): LongArray?
    @JvmStatic private external fun nativeISteamRemoteStorageListFiles(): Array<String>?
    @JvmStatic private external fun nativeISteamRemoteStorageFileExists(name: String): Boolean
    @JvmStatic private external fun nativeISteamRemoteStorageFileRead(name: String): ByteArray?
    @JvmStatic private external fun nativeISteamRemoteStorageFileWrite(name: String, data: ByteArray): Boolean
    @JvmStatic private external fun nativeISteamRemoteStorageFileDelete(name: String): Boolean
    @JvmStatic private external fun nativeISteamRemoteStorageSetCloudEnabledForApp(enabled: Boolean)
    @JvmStatic private external fun nativeISteamUserStatsRequestCurrentStats(): Boolean
    @JvmStatic private external fun nativeISteamUserStatsGetNumAchievements(): Int
    @JvmStatic private external fun nativeISteamUserStatsListAchievements(): Array<String>?
    @JvmStatic private external fun nativeISteamUserStatsGetAchievementAndUnlockTime(name: String): IntArray?
    @JvmStatic private external fun nativeISteamUserStatsSetAchievement(name: String): Boolean
    @JvmStatic private external fun nativeISteamUserStatsClearAchievement(name: String): Boolean
    @JvmStatic private external fun nativeISteamUserStatsStoreStats(): Boolean
    @JvmStatic private external fun nativeISteamUserStatsGetStatInt(name: String): Int
    @JvmStatic private external fun nativeISteamUserStatsGetStatFloat(name: String): Float
    @JvmStatic private external fun nativeISteamUserStatsSetStatInt(name: String, data: Int): Boolean
    @JvmStatic private external fun nativeISteamUserStatsSetStatFloat(name: String, data: Float): Boolean
    @JvmStatic private external fun nativeISteamUserStatsUpdateAvgRateStat(name: String, countThisSession: Float, sessionLength: Double): Boolean
    @JvmStatic private external fun nativeISteamUserStatsGetAchievementDisplayAttribute(name: String, key: String): String?
    @JvmStatic private external fun nativeISteamUserStatsGetAchievementIcon(name: String): Int
    @JvmStatic private external fun nativeISteamFriendsGetPersonaName(): String?
    @JvmStatic private external fun nativeISteamFriendsGetPersonaState(): Int
    @JvmStatic private external fun nativeISteamFriendsGetFriendCount(flags: Int): Int
    @JvmStatic private external fun nativeISteamFriendsListFriends(flags: Int): LongArray
    @JvmStatic private external fun nativeISteamFriendsGetFriendPersonaName(steamId: Long): String?
    @JvmStatic private external fun nativeISteamFriendsGetFriendPersonaState(steamId: Long): Int
    @JvmStatic private external fun nativeSubscribeCallback(id: Int)
    @JvmStatic private external fun nativeUnsubscribeCallback(id: Int)
    @JvmStatic private external fun nativeAwaitCallback(id: Int, timeoutMs: Int): ByteArray?

    fun isSubscribedApp(appId: Int): Boolean {
        if (!initialized) return false
        return try {
            nativeBIsSubscribedApp(appId)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun isAppInstalled(appId: Int): Boolean =
        if (!initialized) false
        else try { nativeISteamAppsBIsAppInstalled(appId) }
             catch (_: UnsatisfiedLinkError) { false }

    fun appInstallDir(appId: Int): String? =
        if (!initialized) null
        else try { nativeISteamAppsGetAppInstallDir(appId) }
             catch (_: UnsatisfiedLinkError) { null }

    fun installedDepots(appId: Int): IntArray =
        if (!initialized) IntArray(0)
        else try { nativeISteamAppsGetInstalledDepots(appId) }
             catch (_: UnsatisfiedLinkError) { IntArray(0) }

    fun currentGameLanguage(): String? =
        if (!initialized) null
        else try { nativeISteamAppsGetCurrentGameLanguage() }
             catch (_: UnsatisfiedLinkError) { null }

    fun isDlcInstalled(dlcAppId: Int): Boolean =
        if (!initialized) false
        else try { nativeISteamAppsBIsDlcInstalled(dlcAppId) }
             catch (_: UnsatisfiedLinkError) { false }

    fun earliestPurchaseUnixTime(appId: Int): Int =
        if (!initialized) 0
        else try { nativeISteamAppsGetEarliestPurchaseUnixTime(appId) }
             catch (_: UnsatisfiedLinkError) { 0 }

    fun dlcCount(appId: Int): Int =
        if (!initialized) 0
        else try { nativeISteamAppsGetDLCCount(appId) }
             catch (_: UnsatisfiedLinkError) { 0 }

    fun appOwner(): Long =
        if (!initialized) 0L
        else try { nativeISteamAppsGetAppOwner() }
             catch (_: UnsatisfiedLinkError) { 0L }

    fun isSubscribedFromFamilySharing(): Boolean =
        if (!initialized) false
        else try { nativeISteamAppsBIsSubscribedFromFamilySharing() }
             catch (_: UnsatisfiedLinkError) { false }

    fun appBuildId(): Int =
        if (!initialized) 0
        else try { nativeISteamAppsGetAppBuildId() }
             catch (_: UnsatisfiedLinkError) { 0 }

    fun loggedOnPublic(): Boolean =
        if (!initialized) false
        else try { nativeISteamUserBLoggedOn() }
             catch (_: UnsatisfiedLinkError) { false }

    fun userHasLicenseForApp(steamId64: Long, appId: Int): Int =
        if (!initialized) 2
        else try { nativeISteamUserHasLicenseForApp(steamId64, appId) }
             catch (_: UnsatisfiedLinkError) { 2 }

    fun liveSteamId(): Long {
        if (!initialized) return 0
        return try {
            nativeISteamUserGetSteamID()
        } catch (_: UnsatisfiedLinkError) {
            0L
        }
    }

    fun currentAppId(): Int {
        if (!initialized) return 0
        return try {
            nativeISteamUtilsGetAppID()
        } catch (_: UnsatisfiedLinkError) {
            0
        }
    }

    fun serverRealTime(): Int =
        if (!initialized) 0
        else try { nativeISteamUtilsGetServerRealTime() }
             catch (_: UnsatisfiedLinkError) { 0 }

    fun ipCountry(): String? =
        if (!initialized) null
        else try { nativeISteamUtilsGetIPCountry() }
             catch (_: UnsatisfiedLinkError) { null }

    fun steamUiLanguage(): String? =
        if (!initialized) null
        else try { nativeISteamUtilsGetSteamUILanguage() }
             catch (_: UnsatisfiedLinkError) { null }

    fun currentBatteryPower(): Int =
        if (!initialized) 255
        else try { nativeISteamUtilsGetCurrentBatteryPower() }
             catch (_: UnsatisfiedLinkError) { 255 }

    fun imageSize(imageHandle: Int): IntArray =
        if (!initialized) intArrayOf(0, 0)
        else try {
            nativeISteamUtilsGetImageSize(imageHandle)
        } catch (_: UnsatisfiedLinkError) { intArrayOf(0, 0) }

    fun imageRGBA(imageHandle: Int, outRgba: ByteArray): Boolean =
        if (!initialized) false
        else try { nativeISteamUtilsGetImageRGBA(imageHandle, outRgba) }
             catch (_: UnsatisfiedLinkError) { false }

    fun cloudFileCount(): Int =
        if (!initialized) 0
        else try { nativeISteamRemoteStorageGetFileCount() }
             catch (_: UnsatisfiedLinkError) { 0 }

    fun cloudEnabledForAccount(): Boolean =
        if (!initialized) false
        else try { nativeISteamRemoteStorageIsCloudEnabledForAccount() }
             catch (_: UnsatisfiedLinkError) { false }

    fun cloudEnabledForApp(): Boolean =
        if (!initialized) false
        else try { nativeISteamRemoteStorageIsCloudEnabledForApp() }
             catch (_: UnsatisfiedLinkError) { false }

    fun cloudQuota(): LongArray =
        if (!initialized) longArrayOf(0L, 0L)
        else try {
            nativeISteamRemoteStorageGetQuota() ?: longArrayOf(0L, 0L)
        } catch (_: UnsatisfiedLinkError) {
            longArrayOf(0L, 0L)
        }

    fun requestCurrentStats(): Boolean =
        if (!initialized) false
        else try { nativeISteamUserStatsRequestCurrentStats() }
             catch (_: UnsatisfiedLinkError) { false }

    fun numAchievements(): Int =
        if (!initialized) 0
        else try { nativeISteamUserStatsGetNumAchievements() }
             catch (_: UnsatisfiedLinkError) { 0 }

    data class CloudFileEntry(val name: String, val size: Int)

    fun listCloudFiles(): List<CloudFileEntry> {
        if (!initialized) return emptyList()
        val raw = try {
            nativeISteamRemoteStorageListFiles()
        } catch (_: UnsatisfiedLinkError) {
            null
        } ?: return emptyList()
        return raw.map { row ->
            val tab = row.indexOf('\t')
            if (tab <= 0) {
                CloudFileEntry(row, -1)
            } else {
                val name = row.substring(0, tab)
                val size = row.substring(tab + 1).toIntOrNull() ?: -1
                CloudFileEntry(name, size)
            }
        }
    }

    fun cloudFileExists(name: String): Boolean =
        if (!initialized) false
        else try { nativeISteamRemoteStorageFileExists(name) }
             catch (_: UnsatisfiedLinkError) { false }

    fun cloudFileRead(name: String): ByteArray? =
        if (!initialized) null
        else try { nativeISteamRemoteStorageFileRead(name) }
             catch (_: UnsatisfiedLinkError) { null }

    fun cloudFileWrite(name: String, data: ByteArray): Boolean =
        if (!initialized) false
        else try { nativeISteamRemoteStorageFileWrite(name, data) }
             catch (_: UnsatisfiedLinkError) { false }

    fun cloudFileDelete(name: String): Boolean =
        if (!initialized) false
        else try { nativeISteamRemoteStorageFileDelete(name) }
             catch (_: UnsatisfiedLinkError) { false }

    fun setCloudEnabledForApp(enabled: Boolean) {
        if (!initialized) return
        try { nativeISteamRemoteStorageSetCloudEnabledForApp(enabled) }
        catch (_: UnsatisfiedLinkError) {}
    }

    data class Achievement(
        val apiName: String,
        val displayName: String?,
        val description: String?,
        val hidden: Boolean,
        val achieved: Boolean,
        val unlockTimeRtime32: Int,
    )

    fun listAchievements(): List<String> =
        if (!initialized) emptyList()
        else try {
            (nativeISteamUserStatsListAchievements() ?: emptyArray()).toList()
        } catch (_: UnsatisfiedLinkError) {
            emptyList()
        }

    fun listAchievementsFull(): List<Achievement> = listAchievements().map { name ->
        val unlock = try {
            nativeISteamUserStatsGetAchievementAndUnlockTime(name)
        } catch (_: UnsatisfiedLinkError) { null }
        val achieved = (unlock?.getOrNull(0) ?: 0) != 0
        val unlockTime = unlock?.getOrNull(1) ?: 0
        val display = try {
            nativeISteamUserStatsGetAchievementDisplayAttribute(name, "name")
        } catch (_: UnsatisfiedLinkError) { null }
        val desc = try {
            nativeISteamUserStatsGetAchievementDisplayAttribute(name, "desc")
        } catch (_: UnsatisfiedLinkError) { null }
        val hidden = try {
            nativeISteamUserStatsGetAchievementDisplayAttribute(name, "hidden") == "1"
        } catch (_: UnsatisfiedLinkError) { false }
        Achievement(name, display, desc, hidden, achieved, unlockTime)
    }

    fun setAchievement(name: String): Boolean =
        if (!initialized) false
        else try { nativeISteamUserStatsSetAchievement(name) }
             catch (_: UnsatisfiedLinkError) { false }

    fun clearAchievement(name: String): Boolean =
        if (!initialized) false
        else try { nativeISteamUserStatsClearAchievement(name) }
             catch (_: UnsatisfiedLinkError) { false }

    fun storeStats(): Boolean =
        if (!initialized) false
        else try { nativeISteamUserStatsStoreStats() }
             catch (_: UnsatisfiedLinkError) { false }

    fun getStatInt(name: String): Int =
        if (!initialized) 0
        else try { nativeISteamUserStatsGetStatInt(name) }
             catch (_: UnsatisfiedLinkError) { 0 }

    fun getStatFloat(name: String): Float =
        if (!initialized) 0.0f
        else try { nativeISteamUserStatsGetStatFloat(name) }
             catch (_: UnsatisfiedLinkError) { 0.0f }

    fun setStatInt(name: String, data: Int): Boolean =
        if (!initialized) false
        else try { nativeISteamUserStatsSetStatInt(name, data) }
             catch (_: UnsatisfiedLinkError) { false }

    fun setStatFloat(name: String, data: Float): Boolean =
        if (!initialized) false
        else try { nativeISteamUserStatsSetStatFloat(name, data) }
             catch (_: UnsatisfiedLinkError) { false }

    fun updateAvgRateStat(name: String, countThisSession: Float, sessionLength: Double): Boolean =
        if (!initialized) false
        else try {
            nativeISteamUserStatsUpdateAvgRateStat(name, countThisSession, sessionLength)
        } catch (_: UnsatisfiedLinkError) { false }

    object Callback {
        const val EncryptedAppTicketResponse  = 154

        const val SteamAPICallCompleted       = 703

        const val PersonaStateChange          = 304
        const val GameOverlayActivated        = 331

        const val SteamAppInstalled           = 1041
        const val SteamAppUninstalled         = 1042

        const val UserStatsReceived           = 1101
        const val UserStatsStored             = 1102
        const val UserAchievementStored       = 1103

        const val RemoteStorageFileShareResult            = 1307
        const val RemoteStorageSubscribePublishedFileResult = 1314
        const val RemoteStorageDownloadUGCResult          = 1317
    }

    fun subscribeCallback(callbackId: Int) {
        if (!initialized) return
        try { nativeSubscribeCallback(callbackId) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun unsubscribeCallback(callbackId: Int) {
        if (!initialized) return
        try { nativeUnsubscribeCallback(callbackId) }
        catch (_: UnsatisfiedLinkError) {}
    }

    object FriendFlags {
        const val None              = 0x00
        const val Blocked           = 0x01
        const val FriendshipRequested = 0x02
        const val Immediate         = 0x04
        const val Clan              = 0x08
        const val OnGameServer      = 0x10
        const val RequestingFriendship = 0x80
        const val RequestingInfo    = 0x100
        const val Ignored           = 0x200
        const val IgnoredFriend     = 0x400
        const val ChatMember        = 0x1000
        const val All               = 0xFFFF
    }

    fun personaName(): String? =
        if (!initialized) null
        else try { nativeISteamFriendsGetPersonaName() }
             catch (_: UnsatisfiedLinkError) { null }

    fun personaState(): Int =
        if (!initialized) 0
        else try { nativeISteamFriendsGetPersonaState() }
             catch (_: UnsatisfiedLinkError) { 0 }

    fun diagnosticRequestEncryptedAppTicket(): Pair<Long, ByteArray> {
        val out = ByteArray(128)
        val h = try {
            com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                .nativeDiagnosticRequestEncryptedAppTicket(out)
        } catch (_: UnsatisfiedLinkError) { 0L }
        return h to out
    }

    fun friendCount(flags: Int = FriendFlags.Immediate): Int =
        if (!initialized) 0
        else try { nativeISteamFriendsGetFriendCount(flags) }
             catch (_: UnsatisfiedLinkError) { 0 }

    fun listFriends(flags: Int = FriendFlags.Immediate): LongArray =
        if (!initialized) LongArray(0)
        else try { nativeISteamFriendsListFriends(flags) }
             catch (_: UnsatisfiedLinkError) { LongArray(0) }

    fun friendPersonaName(steamId: Long): String? =
        if (!initialized) null
        else try { nativeISteamFriendsGetFriendPersonaName(steamId) }
             catch (_: UnsatisfiedLinkError) { null }

    fun friendPersonaState(steamId: Long): Int =
        if (!initialized) 0
        else try { nativeISteamFriendsGetFriendPersonaState(steamId) }
             catch (_: UnsatisfiedLinkError) { 0 }

    fun awaitCallback(callbackId: Int, timeoutMs: Int): ByteArray? {
        if (!initialized) return null
        return try {
            nativeAwaitCallback(callbackId, timeoutMs)
        } catch (_: UnsatisfiedLinkError) {
            null
        }
    }
}
