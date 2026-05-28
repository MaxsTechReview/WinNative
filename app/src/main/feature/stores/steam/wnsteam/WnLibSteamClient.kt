package com.winlator.cmod.feature.stores.steam.wnsteam

import android.util.Log
import org.json.JSONArray

object WnLibSteamClient {
    private const val TAG = "WnLibSteamClient"

    @Volatile private var loaded = false

    fun ensureLoaded(context: android.content.Context): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("steamclient")
            loaded = true
            Log.i(TAG, "libsteamclient.so loaded via System.loadLibrary (APK nativeLibraryDir)")
            true
        } catch (t: UnsatisfiedLinkError) {
            Log.w(TAG, "System.loadLibrary(\"steamclient\") failed: ${t.message}; setters will no-op")
            false
        }
    }

    fun setSteamId(steamId64: Long) {
        if (!loaded) return
        try { nativeSetSteamId(steamId64) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setLoggedOn(loggedOn: Boolean) {
        if (!loaded) return
        try { nativeSetLoggedOn(loggedOn) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setPersonaName(name: String?) {
        if (!loaded) return
        try { nativeSetPersonaName(name.orEmpty()) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setPersonaState(state: Int) {
        if (!loaded) return
        try { nativeSetPersonaState(state) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setAppId(appId: Int) {
        if (!loaded) return
        try { nativeSetAppId(appId) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setIPCountry(country: String?) {
        if (!loaded) return
        try { nativeSetIPCountry(country.orEmpty()) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setUiLanguage(language: String?) {
        if (!loaded) return
        try { nativeSetUiLanguage(language.orEmpty()) } catch (_: UnsatisfiedLinkError) {}
    }

    fun seedFromPrefManager(context: android.content.Context) {
        if (!ensureLoaded(context)) return
        try {
            var sid = com.winlator.cmod.feature.stores.steam.utils
                .PrefManager.steamUserSteamId64
            if (sid == 0L) {
                val rt = com.winlator.cmod.feature.stores.steam.utils
                    .PrefManager.refreshToken
                if (rt.isNotBlank()) {
                    runCatching {
                        val sub = com.auth0.android.jwt.JWT(rt).subject
                        sub?.toLongOrNull()?.let { decoded ->
                            if (decoded != 0L) {
                                sid = decoded
                                com.winlator.cmod.feature.stores.steam.utils
                                    .PrefManager.steamUserSteamId64 = decoded
                                com.winlator.cmod.feature.stores.steam.utils
                                    .PrefManager.steamUserAccountId =
                                    (decoded and 0xFFFFFFFFL).toInt()
                                Log.i(TAG, "seedFromPrefManager: recovered " +
                                    "steamId64=$decoded from refresh-token JWT")
                            }
                        }
                    }.onFailure {
                        Log.w(TAG, "seedFromPrefManager: JWT decode for " +
                            "steamId recovery failed: ${it.message}")
                    }
                }
            }
            if (sid != 0L) {
                nativeSetSteamId(sid)
                nativeSetLoggedOn(true)
            }
            val name = com.winlator.cmod.feature.stores.steam.utils
                .PrefManager.steamUserName
            if (name.isNotEmpty()) nativeSetPersonaName(name)
            val pstate = com.winlator.cmod.feature.stores.steam.utils
                .PrefManager.personaState
            if (pstate != 0) nativeSetPersonaState(pstate)
            val lang = com.winlator.cmod.feature.stores.steam.utils
                .PrefManager.containerLanguage
            if (lang.isNotEmpty()) nativeSetUiLanguage(lang)
            if (sid != 0L) nativeSetCloudEnabledForAccount(true)
            val hashHex = com.winlator.cmod.feature.stores.steam.utils
                .PrefManager.steamUserAvatarHash
            if (sid != 0L && hashHex.isNotEmpty()
                    && hashHex.length % 2 == 0
                    && hashHex.all { it in '0'..'9' || it in 'a'..'f' }) {
                val bytes = ByteArray(hashHex.length / 2)
                for (k in bytes.indices) {
                    val hi = Character.digit(hashHex[k * 2], 16)
                    val lo = Character.digit(hashHex[k * 2 + 1], 16)
                    bytes[k] = ((hi shl 4) or lo).toByte()
                }
                try { nativeSetFriendAvatarHash(sid, bytes) } catch (_: UnsatisfiedLinkError) {}
                AvatarFetcher.enqueueAllTiers(sid, hashHex)
                Log.i(TAG, "seedFromPrefManager: warmed self avatar sid=$sid hash=$hashHex")
            } else {
                Log.i(TAG, "seedFromPrefManager: name='$name' sid=$sid pstate=$pstate lang=$lang " +
                    "(no avatar hash cached)")
            }
        } catch (_: UnsatisfiedLinkError) {}
        try {
            val friendsJson = com.winlator.cmod.feature.stores.steam.utils
                .PrefManager.friendsSnapshotJson
            if (friendsJson.isNotEmpty()) {
                val pushed = pushFriendPersonasJson(friendsJson, persistSnapshot = false)
                Log.i(TAG, "seedFromPrefManager: replayed $pushed friend persona(s) from snapshot")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "seedFromPrefManager: friend snapshot replay failed: ${t.message}")
        }
    }

    fun pushFriendPersonasJson(json: String, persistSnapshot: Boolean = true): Int {
        if (!loaded) return 0
        val arr = try { JSONArray(json) } catch (_: Exception) { return 0 }
        if (arr.length() == 0) return 0
        val sids = ArrayList<Long>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val sid = obj.optLong("sid", 0L)
            if (sid == 0L) continue
            sids.add(sid)

            obj.optString("name", "").takeIf { it.isNotEmpty() }?.let {
                setFriendPersonaName(sid, it)
            }
            obj.optInt("state", -1).takeIf { it >= 0 }?.let {
                setFriendPersonaState(sid, it)
            }
            obj.optInt("app", -1).takeIf { it >= 0 }?.let {
                setFriendGamePlayed(sid, it)
            }
            val hashHex = obj.optString("avatarHash", "")
            if (hashHex.isNotEmpty() && hashHex.length % 2 == 0
                    && hashHex.all { c -> c in '0'..'9' || c in 'a'..'f' }) {
                val bytes = ByteArray(hashHex.length / 2)
                for (k in bytes.indices) {
                    val hi = Character.digit(hashHex[k * 2], 16)
                    val lo = Character.digit(hashHex[k * 2 + 1], 16)
                    bytes[k] = ((hi shl 4) or lo).toByte()
                }
                setFriendAvatarHash(sid, bytes)
                AvatarFetcher.enqueueAllTiers(sid, hashHex)
            }
        }
        if (sids.isNotEmpty()) {
            setFriendsList(sids.toLongArray())
        }
        if (persistSnapshot && sids.isNotEmpty()) {
            com.winlator.cmod.feature.stores.steam.utils
                .PrefManager.friendsSnapshotJson = json
        }
        return sids.size
    }

    fun setOwnedApps(appIds: IntArray) {
        if (!loaded) return
        try { nativeSetOwnedApps(appIds) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setInstalledApps(appIds: IntArray) {
        if (!loaded) return
        try { nativeSetInstalledApps(appIds) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setAppInstallDir(appId: Int, dir: String?) {
        if (!loaded) return
        try { nativeSetAppInstallDir(appId, dir.orEmpty()) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setAppDlcs(
        parentAppId: Int,
        dlcAppIds: IntArray,
        dlcNames: Array<String>,
        available: BooleanArray,
    ) {
        if (!loaded || parentAppId <= 0) return
        if (dlcAppIds.size != dlcNames.size || dlcAppIds.size != available.size) return
        try { nativeSetAppDlcs(parentAppId, dlcAppIds, dlcNames, available) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun setAppInstalledDepots(appId: Int, depotIds: IntArray?) {
        if (!loaded || appId <= 0) return
        try { nativeSetAppInstalledDepots(appId, depotIds) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun setAppWorkshopItems(
        appId: Int,
        publishedFileIds: LongArray,
        installDirs: Array<String>,
        sizesBytes: LongArray,
        timestamps: LongArray,
    ) {
        if (!loaded || appId <= 0) return
        if (publishedFileIds.size != installDirs.size ||
            publishedFileIds.size != sizesBytes.size ||
            publishedFileIds.size != timestamps.size) return
        try { nativeSetAppWorkshopItems(appId, publishedFileIds, installDirs, sizesBytes, timestamps) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun setInventoryItemDefs(
        appId: Int,
        defIds: IntArray,
        propCountsPerDef: IntArray,
        propKeys: Array<String>,
        propVals: Array<String>,
    ) {
        if (!loaded || appId <= 0) return
        if (defIds.size != propCountsPerDef.size) return
        if (propKeys.size != propVals.size) return
        if (propCountsPerDef.sum() != propKeys.size) return
        try { nativeSetInventoryItemDefs(appId, defIds, propCountsPerDef, propKeys, propVals) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun setAppNames(appIds: IntArray, names: Array<String>) {
        if (!loaded || appIds.size != names.size) return
        if (appIds.isEmpty()) return
        try { nativeSetAppNames(appIds, names) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setAppBuildId(appId: Int, buildId: Int) {
        if (!loaded || appId <= 0) return
        try { nativeSetAppBuildId(appId, buildId) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setAppSourcePackages(appId: Int, packageIds: IntArray?) {
        if (!loaded || appId <= 0) return
        try { nativeSetAppSourcePackages(appId, packageIds) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setAppCloudRemoteDir(appId: Int, path: String?) {
        if (!loaded || appId <= 0) return
        try { nativeSetAppCloudRemoteDir(appId, path) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setFriendsList(steamIds: LongArray) {
        if (!loaded) return
        try { nativeSetFriendsList(steamIds) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setFriendPersonaName(steamId64: Long, name: String?) {
        if (!loaded) return
        try { nativeSetFriendPersonaName(steamId64, name.orEmpty()) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setFriendPersonaState(steamId64: Long, state: Int) {
        if (!loaded) return
        try { nativeSetFriendPersonaState(steamId64, state) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setFriendGamePlayed(steamId64: Long, appId: Int) {
        if (!loaded) return
        try { nativeSetFriendGamePlayed(steamId64, appId) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setServerRealTime(serverRealTimeUnix: Int) {
        if (!loaded) return
        try { nativeSetServerRealTime(serverRealTimeUnix) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setLaunchCommandLine(cli: String?) {
        if (!loaded) return
        try { nativeSetLaunchCommandLine(cli.orEmpty()) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun setAppFamilyShared(familyShared: Boolean) {
        if (!loaded) return
        try { nativeSetAppFamilyShared(familyShared) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun setEncryptedAppTicket(appId: Int, body: ByteArray?, eresult: Int) {
        if (!loaded) return
        try { nativeSetEncryptedAppTicket(appId, body, eresult) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun reportLogonFailure(eresult: Int, stillRetrying: Boolean) {
        if (!loaded) return
        try { nativeReportLogonFailure(eresult, stillRetrying) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun setCloudEnabledForAccount(enabled: Boolean) {
        if (!loaded) return
        try { nativeSetCloudEnabledForAccount(enabled) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setCloudEnabledForApp(enabled: Boolean) {
        if (!loaded) return
        try { nativeSetCloudEnabledForApp(enabled) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setCloudQuota(totalBytes: Long, availableBytes: Long) {
        if (!loaded) return
        try { nativeSetCloudQuota(totalBytes, availableBytes) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setCloudFiles(names: Array<String>, sizes: IntArray, timestamps: LongArray) {
        if (!loaded) return
        if (names.size != sizes.size || names.size != timestamps.size) return
        try { nativeSetCloudFiles(names, sizes, timestamps) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setAchievementSchema(
        apiNames: Array<String>,
        displayNames: Array<String>,
        descriptions: Array<String>,
        icons: Array<String>,
        hidden: BooleanArray,
    ) {
        if (!loaded) return
        if (apiNames.size != displayNames.size ||
            apiNames.size != descriptions.size ||
            apiNames.size != icons.size ||
            apiNames.size != hidden.size) return
        try {
            nativeSetAchievementSchema(apiNames, displayNames, descriptions, icons, hidden)
        } catch (_: UnsatisfiedLinkError) {}
    }

    fun setAchievementProgress(apiName: String, achieved: Boolean, unlockTimeUnix: Int) {
        if (!loaded || apiName.isEmpty()) return
        try { nativeSetAchievementProgress(apiName, achieved, unlockTimeUnix) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun setAchievementBlockBits(
        apiNames: Array<String>,
        blockIds: IntArray,
        bitIndices: IntArray,
    ) {
        if (!loaded) return
        if (apiNames.size != blockIds.size || apiNames.size != bitIndices.size) return
        try { nativeSetAchievementBlockBits(apiNames, blockIds, bitIndices) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun setStatIds(names: Array<String>, ids: IntArray) {
        if (!loaded) return
        if (names.size != ids.size) return
        try { nativeSetStatIds(names, ids) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun addAchievementLocale(apiName: String, locale: String,
                             displayName: String?, description: String?) {
        if (!loaded || apiName.isEmpty() || locale.isEmpty()) return
        if (displayName.isNullOrEmpty() && description.isNullOrEmpty()) return
        try {
            nativeAddAchievementLocale(apiName, locale,
                displayName.orEmpty(), description.orEmpty())
        } catch (_: UnsatisfiedLinkError) {}
    }

    fun setStatInt(name: String, value: Int) {
        if (!loaded || name.isEmpty()) return
        try { nativeSetStatInt(name, value) } catch (_: UnsatisfiedLinkError) {}
    }

    fun setStatFloat(name: String, value: Float) {
        if (!loaded || name.isEmpty()) return
        try { nativeSetStatFloat(name, value) } catch (_: UnsatisfiedLinkError) {}
    }

    @JvmStatic private external fun nativeSetSteamId(steamId64: Long)
    @JvmStatic private external fun nativeSetLoggedOn(loggedOn: Boolean)
    @JvmStatic private external fun nativeSetPersonaName(name: String)
    @JvmStatic private external fun nativeSetPersonaState(state: Int)
    @JvmStatic private external fun nativeSetAppId(appId: Int)
    @JvmStatic private external fun nativeSetIPCountry(country: String)
    @JvmStatic private external fun nativeSetUiLanguage(language: String)
    @JvmStatic private external fun nativeSetOwnedApps(appIds: IntArray)
    @JvmStatic private external fun nativeSetInstalledApps(appIds: IntArray)
    @JvmStatic private external fun nativeSetAppInstallDir(appId: Int, dir: String)
    @JvmStatic private external fun nativeSetAppDlcs(
        parentAppId: Int, dlcAppIds: IntArray, dlcNames: Array<String>,
        available: BooleanArray)
    @JvmStatic private external fun nativeSetAppInstalledDepots(appId: Int, depotIds: IntArray?)
    @JvmStatic private external fun nativeSetAppWorkshopItems(
        appId: Int,
        publishedFileIds: LongArray,
        installDirs: Array<String>,
        sizesBytes: LongArray,
        timestamps: LongArray,
    )
    @JvmStatic private external fun nativeSetInventoryItemDefs(
        appId: Int,
        defIds: IntArray,
        propCountsPerDef: IntArray,
        propKeys: Array<String>,
        propVals: Array<String>,
    )
    @JvmStatic private external fun nativeSetAppNames(appIds: IntArray, names: Array<String>)
    @JvmStatic private external fun nativeSetAppBuildId(appId: Int, buildId: Int)
    @JvmStatic private external fun nativeSetFriendsList(steamIds: LongArray)
    @JvmStatic private external fun nativeSetFriendPersonaName(steamId64: Long, name: String)
    @JvmStatic private external fun nativeSetFriendPersonaState(steamId64: Long, state: Int)
    @JvmStatic private external fun nativeSetFriendGamePlayed(steamId64: Long, appId: Int)
    @JvmStatic private external fun nativeSetServerRealTime(serverRealTimeUnix: Int)
    @JvmStatic private external fun nativeReportLogonFailure(eresult: Int, stillRetrying: Boolean)
    @JvmStatic private external fun nativeSetEncryptedAppTicket(appId: Int, body: ByteArray?, eresult: Int)
    @JvmStatic private external fun nativeSetLaunchCommandLine(cli: String)
    @JvmStatic private external fun nativeSetAppFamilyShared(familyShared: Boolean)
    @JvmStatic private external fun nativeSetCloudEnabledForAccount(enabled: Boolean)
    @JvmStatic private external fun nativeSetCloudEnabledForApp(enabled: Boolean)
    @JvmStatic private external fun nativeSetCloudQuota(totalBytes: Long, availBytes: Long)
    @JvmStatic private external fun nativeSetCloudFiles(
        names: Array<String>, sizes: IntArray, timestamps: LongArray)
    @JvmStatic private external fun nativeSetAchievementSchema(
        apiNames: Array<String>, displayNames: Array<String>,
        descriptions: Array<String>, icons: Array<String>, hidden: BooleanArray)
    @JvmStatic private external fun nativeSetAchievementBlockBits(
        apiNames: Array<String>, blockIds: IntArray, bitIndices: IntArray)
    @JvmStatic private external fun nativeSetStatIds(names: Array<String>, ids: IntArray)
    @JvmStatic private external fun nativeSetAchievementProgress(
        apiName: String, achieved: Boolean, unlockTimeUnix: Int)
    @JvmStatic private external fun nativeAddAchievementLocale(
        apiName: String, locale: String, displayName: String, description: String)
    @JvmStatic private external fun nativeSetStatInt(name: String, value: Int)
    @JvmStatic private external fun nativeSetStatFloat(name: String, value: Float)
    @JvmStatic external fun nativeDiagnosticAchievementCount(): Int
    @JvmStatic external fun nativeDiagnosticCallbackDepth(): Int
    @JvmStatic external fun nativeDiagnosticStoreStats(): Boolean
    @JvmStatic external fun nativeDiagnosticSetAchievement(name: String): Boolean
    @JvmStatic external fun nativeDiagnosticIndicateAchievementProgress(
        name: String, current: Int, max: Int): Boolean
    @JvmStatic external fun nativeSetFriendRichPresence(
        steamId: Long, key: String, value: String?)
    @JvmStatic external fun nativeDiagnosticSetRichPresence(
        key: String, value: String?): Boolean
    @JvmStatic external fun nativeDiagnosticClearRichPresence()
    @JvmStatic external fun nativeDiagnosticRichPresenceKeyCount(steamId: Long): Int
    @JvmStatic external fun nativeDiagnosticSetPersonaName(name: String): Long
    @JvmStatic external fun nativeDiagnosticRequestUserInformation(steamId: Long, nameOnly: Boolean): Boolean
    @JvmStatic external fun nativeDiagnosticRequestFriendRichPresence(steamId: Long)
    @JvmStatic external fun nativeDiagnosticRequestUserInfoBulk(sids: LongArray, flags: Int): Boolean
    @JvmStatic external fun nativeDiagnosticGetCachedOwnershipTicket(
        appId: Int, out: ByteArray?): Int
    @JvmStatic external fun nativeDiagnosticInjectOwnershipTicket(
        appId: Int, bytes: ByteArray): Boolean
    @JvmStatic external fun nativeDiagnosticInjectLogonState(loggedOn: Boolean)
    @JvmStatic external fun nativeDiagnosticInjectFriendsList(sids: LongArray?)
    @JvmStatic external fun nativeDiagnosticInjectLicenseList(
        packageIds: IntArray, ownerIds: IntArray?)
    @JvmStatic external fun nativeDiagnosticGetLicenseOwner(packageId: Int): Int
    @JvmStatic external fun nativeSetAppSourcePackages(
        appId: Int, packageIds: IntArray?)
    @JvmStatic external fun nativeDiagnosticGetEarliestPurchaseUnixTime(appId: Int): Int
    @JvmStatic external fun nativeDiagnosticBIsSubscribedFromFreeWeekend(): Boolean
    @JvmStatic external fun nativeDiagnosticBIsSubscribedFromFamilySharing(): Boolean
    @JvmStatic external fun nativeDiagnosticGetAppOwner(): Long
    @JvmStatic external fun nativeDiagnosticInjectTrialLicense(
        packageId: Int, minuteLimit: Int, minutesUsed: Int)
    @JvmStatic external fun nativeDiagnosticBIsTimedTrial(): Long
    @JvmStatic external fun nativeDiagnosticBIsDlcInstalled(appId: Int): Boolean
    @JvmStatic external fun nativeSetAppCurrentBeta(appId: Int, branch: String?)

    fun setAppCurrentBeta(appId: Int, branch: String?) {
        if (!loaded) return
        try { nativeSetAppCurrentBeta(appId, branch) } catch (_: UnsatisfiedLinkError) {}
    }
    @JvmStatic external fun nativeDiagnosticGetCurrentBetaName(): String?
    @JvmStatic external fun nativeSetAppDownloadProgress(
        appId: Int, bytesDownloaded: Long, bytesTotal: Long)
    @JvmStatic external fun nativeDiagnosticGetDlcDownloadProgress(appId: Int): Boolean
    @JvmStatic external fun nativeDiagnosticGetDlcDownloadProgressBytes(): Long
    @JvmStatic external fun nativeDiagnosticGetDlcDownloadProgressTotal(): Long
    @JvmStatic external fun nativeSetAppCloudRemoteDir(appId: Int, path: String?)
    @JvmStatic external fun nativeDiagnosticCloudFileWrite(
        name: String, data: ByteArray): Boolean
    @JvmStatic external fun nativeDiagnosticCloudFileRead(
        name: String, maxBytes: Int): ByteArray?
    @JvmStatic external fun nativeDiagnosticCloudFileDelete(name: String): Boolean
    @JvmStatic external fun nativeDiagnosticCloudFileWriteAsync(
        name: String, data: ByteArray): Long
    @JvmStatic external fun nativeDiagnosticCloudFileReadAsync(
        name: String, offset: Int, cubToRead: Int): Long
    @JvmStatic external fun nativeDiagnosticCloudFileReadAsyncComplete(
        hCall: Long, cubToRead: Int): ByteArray?
    @JvmStatic external fun nativeDiagnosticCloudStreamOpen(name: String): Long
    @JvmStatic external fun nativeDiagnosticCloudStreamWriteChunk(
        hStream: Long, data: ByteArray): Boolean
    @JvmStatic external fun nativeDiagnosticCloudStreamClose(hStream: Long): Boolean
    @JvmStatic external fun nativeDiagnosticCloudStreamCancel(hStream: Long): Boolean
    @JvmStatic external fun nativeSetAppFlag(flagKind: Int, appId: Int, on: Boolean)
    @JvmStatic external fun nativeDiagnosticAppsBool(slot: Int): Boolean
    @JvmStatic external fun nativeDiagnosticSetDlcContext(appId: Int): Boolean
    @JvmStatic external fun nativeDiagnosticCloudFileForget(name: String): Boolean
    @JvmStatic external fun nativeDiagnosticCloudFilePersisted(name: String): Boolean
    @JvmStatic external fun nativeSetAccountFlag(flagKind: Int, on: Boolean)
    @JvmStatic external fun nativeDiagnosticUserBool(slot: Int): Boolean
    @JvmStatic external fun nativeDiagnosticSetDurationControl(state: Int): Boolean
    @JvmStatic external fun nativeDiagnosticGetUserDataFolder(): String?
    @JvmStatic external fun nativeDiagnosticGetFriendRelationship(sid: Long): Int
    @JvmStatic external fun nativeDiagnosticHasFriend(sid: Long, flags: Int): Boolean
    @JvmStatic external fun nativeDiagnosticGetAuthTicketForWebApi(identity: String?): Long
    @JvmStatic external fun nativeSetFriendSteamLevel(sid: Long, level: Int)
    @JvmStatic external fun nativeIsAppMarkedCorrupt(appId: Int): Boolean
    @JvmStatic external fun nativeClearAppCorruptFlag(appId: Int)
    @JvmStatic external fun nativeDiagnosticUserHasLicense(sid: Long, appId: Int): Int
    @JvmStatic external fun nativeDiagnosticMarkContentCorrupt(missingOnly: Boolean): Boolean
    @JvmStatic external fun nativeDiagnosticGetFriendSteamLevel(sid: Long): Int
    @JvmStatic external fun nativeSetSelfPlayerLevel(level: Int)
    @JvmStatic external fun nativeSetSelfGameBadge(appId: Int, nSeries: Int, bFoil: Boolean, tier: Int)
    @JvmStatic external fun nativeDiagnosticGetPlayerSteamLevel(): Int
    @JvmStatic external fun nativeDiagnosticGetGameBadgeLevel(nSeries: Int, bFoil: Boolean): Int
    @JvmStatic external fun nativeDiagnosticRequestStoreAuthURL(redirect: String?): Long
    @JvmStatic external fun nativeDiagnosticGetMarketEligibility(): Long
    @JvmStatic external fun nativeDiagnosticGetDurationControl(): Long
    @JvmStatic external fun nativeDiagnosticCloudFileShare(name: String): Long
    @JvmStatic external fun nativeDiagnosticAppsGetFileDetails(name: String): Long
    @JvmStatic external fun nativeSetPlayerNickname(sid: Long, nickname: String?)
    @JvmStatic external fun nativeDiagnosticGetPlayerNickname(sid: Long): String?
    @JvmStatic external fun nativeDiagnosticCheckFileSignature(name: String?): Long
    @JvmStatic external fun nativeGetPushedSteamId(): Long
    @JvmStatic external fun nativeGetPushedPersonaName(): String
    @JvmStatic external fun nativeGetPushedIpCountry(): String
    @JvmStatic external fun nativeGetPushedPersonaState(): Int
    @JvmStatic external fun nativeGetPushedLoggedOn(): Boolean
    @JvmStatic external fun nativeGetPushedAppId(): Int
    @JvmStatic external fun nativeGetPushedOwnedAppCount(): Int
    @JvmStatic external fun nativeGetPushedInstalledAppCount(): Int
    @JvmStatic external fun nativeGetPushedFriendCount(): Int
    @JvmStatic external fun nativeGetPushedFirstFriend(): Long
    @JvmStatic external fun nativeGetPushedUiLanguage(): String
    @JvmStatic external fun nativeGetPushedServerRealTime(): Int
    @JvmStatic external fun nativeGetPushedCloudFileCount(): Int
    @JvmStatic external fun nativeGetPushedCloudEnabledAccount(): Boolean
    @JvmStatic external fun nativeGetPushedCloudEnabledApp(): Boolean
    @JvmStatic external fun nativeGetPushedEncryptedAppTicketSize(appId: Int): Int
    @JvmStatic external fun nativeDiagnosticInjectAccountInfo(
        twoFactor: Boolean, phoneVerified: Boolean,
        phoneIdentifying: Boolean, phoneNeedsVerification: Boolean)
    @JvmStatic external fun nativeDiagnosticUpdateAvgRateStat(
        name: String, countThisSession: Float, sessionLength: Double): Float
    @JvmStatic external fun nativeDiagnosticInjectPersonaEvent(
        steamId: Long,
        personaState: Int,
        gameAppId: Int,
        name: String?,
        avatarHash: ByteArray?,
        rpKeys: Array<String>?,
        rpValues: Array<String>?,
    )

    @JvmStatic external fun nativeSetGameOverlayActive(active: Boolean)

    fun setGameOverlayActive(active: Boolean) {
        try { nativeSetGameOverlayActive(active) }
        catch (_: UnsatisfiedLinkError) { /* .so not loaded yet */ }
    }

    @JvmStatic external fun nativePollOverlayRequest(): String?

    fun pollOverlayRequest(): String? =
        try { nativePollOverlayRequest() }
        catch (_: UnsatisfiedLinkError) { null }

    @JvmStatic external fun nativePushFriendAvatar(
        steamId: Long, tier: Int, width: Int, height: Int, rgba: ByteArray): Int
    @JvmStatic external fun nativeDiagnosticGetSmallAvatarSize(steamId: Long): Long
    @JvmStatic external fun nativeDiagnosticGetTieredAvatarSize(steamId: Long, tier: Int): Long
    @JvmStatic external fun nativeDiagnosticGetImageRGBA(handle: Int, out: ByteArray): Int

    @JvmStatic external fun nativeSetFriendAvatarHash(steamId: Long, hash: ByteArray?)
    @JvmStatic external fun nativeDiagnosticGetFriendAvatarHashHex(steamId: Long): String

    @JvmStatic external fun nativeDiagnosticGetFriendPersonaState(steamId: Long): Int

    fun setFriendAvatarHash(steamId: Long, hash: ByteArray?) {
        try { nativeSetFriendAvatarHash(steamId, hash) }
        catch (_: UnsatisfiedLinkError) { /* .so not loaded yet */ }
    }

    fun setFriendRichPresence(steamId: Long, key: String, value: String?) {
        try {
            nativeSetFriendRichPresence(steamId, key, value)
        } catch (_: UnsatisfiedLinkError) { /* .so not loaded yet */ }
    }
    @JvmStatic external fun nativeDiagnosticRegisterAndDrain(iCallback: Int): Int
    @JvmStatic external fun nativeDiagnosticPushAndDrainCallResult(callbackId: Int, eresult: Int): Long
    @JvmStatic external fun nativeDiagnosticGetAuthTicket(buf: ByteArray): Int
    @JvmStatic external fun nativeDiagnosticRequestEncryptedAppTicket(outBody: ByteArray): Long
    @JvmStatic external fun nativeDiagnosticUtilsGetAPICallResult(callbackId: Int, eresult: Int): Int
    @JvmStatic external fun nativeDiagnosticTcpAccepted(): Int
    @JvmStatic external fun nativeDiagnosticShutdownPipe(): Boolean

    fun shutdownPipe(): Boolean =
        if (!loaded) false
        else try { nativeDiagnosticShutdownPipe() } catch (_: UnsatisfiedLinkError) { false }
}
