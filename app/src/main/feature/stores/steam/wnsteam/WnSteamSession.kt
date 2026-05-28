// JNI symbols depend on this package path and class name.
// Update app/src/main/cpp/wn-steam-client/jni/wn_session_jni.cpp if either changes.
package com.winlator.cmod.feature.stores.steam.wnsteam

import java.util.concurrent.atomic.AtomicLong

// Production handle for the native Steam CM client and auth sessions.
class WnSteamSession : AutoCloseable {

    private val nativeHandle: AtomicLong

    init {
        WnSteamClient.ensureLoaded()
        val h = nativeCreate()
        require(h != 0L) { "wnsteam: nativeCreate returned 0" }
        nativeHandle = AtomicLong(h)
    }

    fun setCaBundlePath(path: String) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSetCaBundlePath(h, path)
    }

    fun setStateObserver(observer: WnSteamStateObserver?) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSetStateObserver(h, observer)
    }

    // Toggle the post-logon library PICS crawl before [logonWithRefreshToken].
    fun setAutoPopulateLibrary(enabled: Boolean) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSetAutoPopulateLibrary(h, enabled)
    }

    fun connect(url: String): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeConnect(h, url)
    }

    fun disconnect() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeDisconnect(h)
    }

    fun logOffAndDisconnect(flushMs: Int = 500) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeLogOffAndDisconnect(h, flushMs)
    }

    fun renewRefreshToken(
        currentToken: String,
        steamId64: Long,
        timeoutMs: Int = 15_000,
    ): String? {
        if (currentToken.isEmpty() || steamId64 == 0L) return null
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeRenewRefreshToken(h, currentToken, steamId64, timeoutMs)
    }

    fun startLoginWithCredentials(
        username: String,
        password: String,
        persistentSession: Boolean,
        authenticator: WnAuthenticator,
        callback: WnAuthCallback,
    ) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeStartLoginWithCredentials(h, username, password, persistentSession,
            authenticator, callback)
    }

    // [resultCallback] may receive a remote-approval update before the final
    // token-bearing success result when the Steam Guard app approves the QR.
    fun startLoginWithQr(
        qrCallback: WnQrCallback,
        resultCallback: WnAuthCallback,
    ) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeStartLoginWithQr(h, qrCallback, resultCallback)
    }

    fun cancelLogin() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeCancelLogin(h)
    }

    // Log on with a refresh token. [accountName] is preferred when Steam provides it,
    // but auth polling can omit it after device confirmation.
    fun logonWithRefreshToken(refreshToken: String, accountName: String, steamId: Long = 0L): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeLogonWithRefreshToken(h, refreshToken, accountName, steamId)
    }

    // Pre-warm app and DLC PICS metadata before launching a game.
    fun prepareApp(appId: Int, dlcAppIds: IntArray, callback: WnPrepareAppCallback) {
        val h = nativeHandle.get()
        if (h == 0L) {
            callback.onPrepareResult(false, "session closed")
            return
        }
        nativePrepareApp(h, appId, dlcAppIds, callback)
    }

    // Start an async native depot download; [listener] runs on a worker thread.
    fun downloadApp(
        appId: Int,
        depotIds: IntArray,
        manifestIds: LongArray,
        branch: String,
        installDir: String,
        fresh: Boolean,
        caBundlePath: String,
        maxWorkers: Int,
        listener: WnDownloadListener,
    ) {
        require(depotIds.size == manifestIds.size) {
            "wnsteam: depotIds/manifestIds size mismatch"
        }
        val h = nativeHandle.get()
        if (h == 0L) {
            listener.onComplete(false, "session closed", 0L, 0, 0)
            return
        }
        nativeDownloadApp(h, appId, depotIds, manifestIds, branch, installDir,
                          fresh, caBundlePath, maxWorkers, listener)
    }

    // Abort the current depot download.
    fun cancelDownload() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeCancelDownload(h)
    }

    fun startWineBridge(steam3Port: Int = 0, clientServicePort: Int = 0): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeStartWineBridge(h, steam3Port, clientServicePort)
    }

    fun stopWineBridge() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeStopWineBridge(h)
    }

    fun wineBridgeLastError(): String {
        val h = nativeHandle.get(); if (h == 0L) return ""
        return nativeWineBridgeLastError(h)
    }

    // Return a cached app ownership ticket, or null if not pre-warmed.
    fun getAppOwnershipTicket(appId: Int): ByteArray? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetAppOwnershipTicket(h, appId)
    }

    // Blocking encrypted-app-ticket request; returns null on failure.
    fun requestEncryptedAppTicket(appId: Int): ByteArray? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeRequestEncryptedAppTicket(h, appId)
    }

    // Blocking user-stats schema request; call off the main thread.
    fun getUserStatsSchema(appId: Int): ByteArray? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetUserStatsSchema(h, appId)
    }

    // Blocking full user-stats request for achievement write-back.
    fun getUserStatsFull(appId: Int): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetUserStatsFull(h, appId)
    }

    // Blocking Steam Inventory item-definition archive fetch.
    fun getItemDefArchive(appId: Int, caBundlePath: String): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        val bytes = nativeGetItemDefArchive(h, appId, caBundlePath) ?: return null
        return String(bytes, Charsets.UTF_8)
    }

    // Blocking subscribed-Workshop-items fetch; returns "[]" when empty.
    fun getSubscribedWorkshopItems(appId: Int): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetSubscribedWorkshopItems(h, appId)
    }

    // Blocking Workshop item depot download; returns bytes written or -1.
    fun downloadWorkshopItem(
        appId: Int,
        manifestId: Long,
        installDir: String,
        caBundlePath: String,
        maxWorkers: Int = 8,
    ): Long {
        val h = nativeHandle.get(); if (h == 0L) return -1L
        return nativeDownloadWorkshopItem(h, appId, manifestId, installDir, caBundlePath, maxWorkers)
    }

    // Fire-and-forget stat or achievement write-back.
    fun storeUserStats(
        appId: Int,
        steamId: Long,
        crcStats: Int,
        statIds: IntArray,
        statValues: IntArray,
    ) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeStoreUserStats(h, appId, steamId, crcStats, statIds, statValues)
    }

    // Blocking Steam Cloud changelist fetch.
    fun getCloudFileList(appId: Int): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetCloudFileList(h, appId)
    }

    fun getCloudUserQuota(): LongArray? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return try { nativeGetCloudUserQuota(h) } catch (_: UnsatisfiedLinkError) { null }
    }

    fun getCloudDownloadInfo(appId: Int, filename: String): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetCloudDownloadInfo(h, appId, filename)
    }

    // Blocking Steam Cloud file download; decompresses Steam's zip wrapper.
    fun downloadCloudFile(appId: Int, filename: String): ByteArray? {
        val infoJson = getCloudDownloadInfo(appId, filename) ?: return null
        return try {
            val obj = org.json.JSONObject(infoJson)
            val host = obj.optString("urlHost")
            if (host.isEmpty()) return null
            val fileSize = obj.optInt("fileSize", 0)
            val rawFileSize = obj.optInt("rawFileSize", 0)
            val url = java.net.URL("https://$host${obj.optString("urlPath")}")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                obj.optJSONArray("headers")?.let { headers ->
                    for (i in 0 until headers.length()) {
                        val hd = headers.getJSONObject(i)
                        setRequestProperty(hd.optString("name"), hd.optString("value"))
                    }
                }
            }
            val raw = try {
                val code = conn.responseCode
                if (code != 200) {
                    android.util.Log.w("WnSteamSession", "cloud GET $filename → HTTP $code")
                    return null
                }
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
            // Steam may serve compressed files as a single-entry zip.
            val body =
                if (fileSize != rawFileSize && raw.isNotEmpty()) {
                    java.util.zip.ZipInputStream(raw.inputStream()).use { zin ->
                        zin.nextEntry ?: run {
                            android.util.Log.w("WnSteamSession", "cloud file $filename: empty zip")
                            return null
                        }
                        zin.readBytes()
                    }
                } else {
                    raw
                }
            // Never let a partial HTTP read replace a good local save.
            if (body.size != rawFileSize) {
                android.util.Log.w(
                    "WnSteamSession",
                    "cloud file $filename: size mismatch (got ${body.size}, expected $rawFileSize) — rejecting",
                )
                return null
            }
            body
        } catch (e: Exception) {
            android.util.Log.w("WnSteamSession", "cloud file download failed: $filename", e)
            null
        }
    }

    // Result of [beginCloudUploadBatch].
    data class CloudUploadBatch(val batchId: Long, val appChangeNumber: Long)

    // Blocking Steam Cloud upload-batch opener.
    fun beginCloudUploadBatch(
        appId: Int,
        fileNames: List<String>,
        filesToDelete: List<String>,
        clientId: Long,
    ): CloudUploadBatch? {
        val h = nativeHandle.get(); if (h == 0L) return null
        val json = nativeCloudBeginUploadBatch(
            h, appId, fileNames.joinToString("\n"), filesToDelete.joinToString("\n"), clientId,
        ) ?: return null
        return try {
            val obj = org.json.JSONObject(json)
            val batchId = obj.optLong("batchId", 0L)
            if (batchId == 0L) null
            else CloudUploadBatch(batchId, obj.optLong("appChangeNumber", 0L))
        } catch (e: Exception) {
            null
        }
    }

    // Blocking file upload within an open Steam Cloud batch.
    fun uploadCloudFile(
        appId: Int,
        filename: String,
        fileBytes: ByteArray,
        fileShaHex: String,
        timestamp: Long,
        batchId: Long,
    ): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        // Cloud uploads are not compressed.
        val beginJson = nativeCloudBeginFileUpload(
            h, appId, filename, fileBytes.size, fileBytes.size, fileShaHex, timestamp, batchId,
        ) ?: return false
        try {
            val blocks0 = org.json.JSONObject(beginJson).optJSONArray("blocks")
            if (blocks0 == null || blocks0.length() == 0) {
                android.util.Log.i(
                    "WnSteamSession",
                    "cloud upload short-circuit: blocks=0 for $filename — file already in cloud, treating as success"
                )
                nativeCloudCommitFileUpload(h, true, appId, fileShaHex, filename)
                return true
            }
        } catch (e: Exception) {
            android.util.Log.w("WnSteamSession", "uploadCloudFile early-parse failed: $filename", e)
        }
        var allOk = true
        try {
            val blocks = org.json.JSONObject(beginJson).optJSONArray("blocks")
            if (blocks != null) {
                for (i in 0 until blocks.length()) {
                    val blk = blocks.getJSONObject(i)
                    val host = blk.optString("urlHost")
                    val off = blk.optLong("blockOffset", 0L).toInt()
                    val len = blk.optInt("blockLength", 0)
                    if (host.isEmpty() || off < 0 || len < 0 || off.toLong() + len > fileBytes.size) {
                        allOk = false
                        continue
                    }
                    val url = java.net.URL("https://$host${blk.optString("urlPath")}")
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "PUT"
                        doOutput = true
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        setFixedLengthStreamingMode(len)
                        blk.optJSONArray("headers")?.let { hs ->
                            for (k in 0 until hs.length()) {
                                val hd = hs.getJSONObject(k)
                                setRequestProperty(hd.optString("name"), hd.optString("value"))
                            }
                        }
                        setRequestProperty("User-Agent", "Valve/Steam HTTP Client 1.0")
                    }
                    try {
                        conn.outputStream.use { it.write(fileBytes, off, len) }
                        val code = conn.responseCode
                        if (code !in 200..299) {
                            allOk = false
                            android.util.Log.w("WnSteamSession", "cloud PUT block $i → HTTP $code")
                        }
                    } catch (e: Exception) {
                        allOk = false
                        android.util.Log.w("WnSteamSession", "cloud PUT block $i failed", e)
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("WnSteamSession", "uploadCloudFile parse failed: $filename", e)
            allOk = false
        }
        return nativeCloudCommitFileUpload(h, allOk, appId, fileShaHex, filename)
    }

    // Blocking Steam Cloud upload-batch completion.
    fun completeCloudUploadBatch(appId: Int, batchId: Long, batchEresult: Int): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeCloudCompleteUploadBatch(h, appId, batchId, batchEresult)
    }

    // Blocking PICS change poll since [sinceChangeNumber].
    fun getPicsChangesSince(sinceChangeNumber: Long): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetPicsChangesSince(h, sinceChangeNumber)
    }

    // Blocking PICS app-info fetch; [accessToken] is 0 for public appinfo.
    fun getPicsAppInfo(appId: Int, accessToken: Long = 0L): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetPicsAppInfo(h, appId, accessToken)
    }

    // Blocking PICS access-token request.
    fun getPicsAccessTokens(appIds: List<Int>, packageIds: List<Int>): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetPicsAccessTokens(
            h, appIds.joinToString("\n"), packageIds.joinToString("\n"),
        )
    }

    // Blocking batch PICS app product-info fetch.
    fun getPicsAppProductInfo(appIds: List<Int>, tokens: List<Long>): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetPicsAppProductInfo(
            h, appIds.joinToString("\n"), tokens.joinToString("\n"),
        )
    }

    // Blocking batch PICS package product-info fetch.
    fun getPicsPackageInfo(packageIds: List<Int>, tokens: List<Long>): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetPicsPackageInfo(
            h, packageIds.joinToString("\n"), tokens.joinToString("\n"),
        )
    }

    // Fire-and-forget running-games presence report.
    fun notifyGamesPlayed(gamesJson: String, clientOsType: Int) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeNotifyGamesPlayed(h, gamesJson, clientOsType)
    }

    // Fire-and-forget request to release another active playing session.
    fun kickPlayingSession(onlyStopGame: Boolean = false) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeKickPlayingSession(h, onlyStopGame)
    }

    // Cached playing-blocked state for this account.
    fun isPlayingBlocked(): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeIsPlayingBlocked(h)
    }

    // Mark playing blocked before waiting for a post-kick server push.
    fun markPlayingBlocked() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeMarkPlayingBlocked(h)
    }

    // Fire-and-forget Steam persona state update.
    fun setPersonaState(personaState: Int) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSetPersonaState(h, personaState)
    }

    fun setPersonaName(name: String, personaState: Int = 1) {
        if (name.isEmpty()) return
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSetPersonaName(h, name, personaState)
    }

    fun requestUserPersona() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeRequestUserPersona(h)
    }

    fun requestFriendPersonas(
        steamIds: LongArray,
        personaStateRequested: Int = 1,
    ) {
        val h = nativeHandle.get(); if (h == 0L) return
        if (steamIds.isEmpty()) return
        try { nativeRequestFriendPersonas(h, steamIds, personaStateRequested) }
        catch (_: UnsatisfiedLinkError) {}
    }

    fun getSelfPersona(): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetSelfPersona(h)
    }

    // Blocking Steam Family group lookup.
    fun getFamilyGroup(familyGroupId: Long): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetFamilyGroup(h, familyGroupId)
    }

    // Cached license list JSON; empty until the post-logon push arrives.
    fun getLicenseList(): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetLicenseList(h)
    }

    fun getFriendsList(): LongArray {
        val h = nativeHandle.get(); if (h == 0L) return LongArray(0)
        return try { nativeGetFriendsList(h) } catch (_: UnsatisfiedLinkError) { LongArray(0) }
    }

    fun getFriendPersonas(): String {
        val h = nativeHandle.get(); if (h == 0L) return "[]"
        return try { nativeGetFriendPersonas(h) ?: "[]" }
               catch (_: UnsatisfiedLinkError) { "[]" }
    }

    fun getOwnedGames(steamId: Long): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetOwnedGames(h, steamId)
    }

    // Blocking launch-intent signal; empty pending-op list means clear to launch.
    fun signalAppLaunchIntent(
        appId: Int,
        clientId: Long,
        machineName: String,
        ignorePending: Boolean,
        osType: Int,
    ): List<Int>? {
        val h = nativeHandle.get(); if (h == 0L) return null
        val json = nativeSignalAppLaunchIntent(h, appId, clientId, machineName, ignorePending, osType)
            ?: return null
        return try {
            val arr = org.json.JSONObject(json).optJSONArray("pendingOps")
            (0 until (arr?.length() ?: 0)).map { arr!!.getInt(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun signalAppExitSyncDone(
        appId: Int,
        clientId: Long,
        uploadsCompleted: Boolean,
        uploadsRequired: Boolean,
    ) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSignalAppExitSyncDone(h, appId, clientId, uploadsCompleted, uploadsRequired)
    }

    // Fresh JSON snapshot of the native library store.
    fun getLibrarySnapshotJson(): String {
        val h = nativeHandle.get(); if (h == 0L) return "{}"
        return nativeGetLibrarySnapshot(h)
    }

    // Install or clear the native library-store observer.
    fun setLibraryObserver(observer: WnLibraryObserver?) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSetLibraryObserver(h, observer)
    }

    fun state(): Int {
        val h = nativeHandle.get(); if (h == 0L) return 0
        return nativeState(h)
    }

    fun steamId(): Long {
        val h = nativeHandle.get(); if (h == 0L) return 0L
        return nativeSteamId(h)
    }

    // Steam Family group id from logon, or 0.
    fun familyGroupId(): Long {
        val h = nativeHandle.get(); if (h == 0L) return 0L
        return nativeFamilyGroupId(h)
    }

    override fun close() {
        val h = nativeHandle.getAndSet(0L)
        if (h != 0L) nativeDestroy(h)
    }

    @Suppress("ProtectedInFinal", "unused")
    protected fun finalize() { close() }

    companion object {
        // Blocking Steam CM WSS resolver; returns empty string on failure.
        fun pickCmUrl(caBundlePath: String): String {
            WnSteamClient.ensureLoaded()
            return nativePickCmUrl(caBundlePath)
        }

        @JvmStatic private external fun nativePickCmUrl(caBundlePath: String): String

        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeSetCaBundlePath(handle: Long, path: String)
        @JvmStatic private external fun nativeSetStateObserver(handle: Long, observer: WnSteamStateObserver?)
        @JvmStatic private external fun nativeSetAutoPopulateLibrary(handle: Long, enabled: Boolean)
        @JvmStatic private external fun nativeConnect(handle: Long, url: String): Boolean
        @JvmStatic private external fun nativeDisconnect(handle: Long)
        @JvmStatic private external fun nativeLogOffAndDisconnect(handle: Long, flushMs: Int)
        @JvmStatic private external fun nativeRenewRefreshToken(
            handle: Long,
            currentToken: String,
            steamId64: Long,
            timeoutMs: Int,
        ): String?
        @JvmStatic private external fun nativeStartLoginWithCredentials(
            handle: Long,
            username: String,
            password: String,
            persistentSession: Boolean,
            authenticator: WnAuthenticator,
            callback: WnAuthCallback,
        )
        @JvmStatic private external fun nativeStartLoginWithQr(
            handle: Long,
            qrCallback: WnQrCallback,
            resultCallback: WnAuthCallback,
        )
        @JvmStatic private external fun nativeCancelLogin(handle: Long)
        @JvmStatic private external fun nativeLogonWithRefreshToken(
            handle: Long,
            refreshToken: String,
            accountName: String,
            steamId: Long,
        ): Boolean
        @JvmStatic private external fun nativePrepareApp(
            handle: Long,
            appId: Int,
            dlcAppIds: IntArray,
            callback: WnPrepareAppCallback,
        )
        @JvmStatic private external fun nativeDownloadApp(
            handle: Long,
            appId: Int,
            depotIds: IntArray,
            manifestIds: LongArray,
            branch: String,
            installDir: String,
            fresh: Boolean,
            caBundlePath: String,
            maxWorkers: Int,
            listener: WnDownloadListener,
        )
        @JvmStatic private external fun nativeCancelDownload(handle: Long)
        @JvmStatic private external fun nativeStartWineBridge(
            handle: Long, steam3Port: Int, clientServicePort: Int): Boolean
        @JvmStatic private external fun nativeStopWineBridge(handle: Long)
        @JvmStatic private external fun nativeWineBridgeLastError(handle: Long): String
        @JvmStatic private external fun nativeGetAppOwnershipTicket(handle: Long, appId: Int): ByteArray?
        @JvmStatic private external fun nativeRequestEncryptedAppTicket(handle: Long, appId: Int): ByteArray?
        @JvmStatic private external fun nativeGetUserStatsSchema(handle: Long, appId: Int): ByteArray?
        @JvmStatic private external fun nativeGetUserStatsFull(handle: Long, appId: Int): String?
        @JvmStatic private external fun nativeGetItemDefArchive(
            handle: Long,
            appId: Int,
            caBundlePath: String,
        ): ByteArray?
        @JvmStatic private external fun nativeGetSubscribedWorkshopItems(
            handle: Long,
            appId: Int,
        ): String?
        @JvmStatic private external fun nativeDownloadWorkshopItem(
            handle: Long,
            appId: Int,
            manifestId: Long,
            installDir: String,
            caBundlePath: String,
            maxWorkers: Int,
        ): Long
        @JvmStatic private external fun nativeStoreUserStats(
            handle: Long, appId: Int, steamId: Long, crcStats: Int,
            statIds: IntArray, statValues: IntArray)
        @JvmStatic private external fun nativeGetCloudFileList(handle: Long, appId: Int): String?
        @JvmStatic private external fun nativeGetCloudUserQuota(handle: Long): LongArray?
        @JvmStatic private external fun nativeGetCloudDownloadInfo(handle: Long, appId: Int, filename: String): String?
        @JvmStatic private external fun nativeCloudBeginUploadBatch(handle: Long, appId: Int, files: String, filesToDelete: String, clientId: Long): String?
        @JvmStatic private external fun nativeCloudBeginFileUpload(handle: Long, appId: Int, filename: String, fileSize: Int, rawFileSize: Int, shaHex: String, timestamp: Long, batchId: Long): String?
        @JvmStatic private external fun nativeCloudCommitFileUpload(handle: Long, transferSucceeded: Boolean, appId: Int, shaHex: String, filename: String): Boolean
        @JvmStatic private external fun nativeCloudCompleteUploadBatch(handle: Long, appId: Int, batchId: Long, batchEresult: Int): Boolean
        @JvmStatic private external fun nativeGetPicsChangesSince(handle: Long, sinceChangeNumber: Long): String?
        @JvmStatic private external fun nativeGetPicsAppInfo(handle: Long, appId: Int, accessToken: Long): String?
        @JvmStatic private external fun nativeGetPicsAccessTokens(handle: Long, appIds: String, packageIds: String): String?
        @JvmStatic private external fun nativeGetPicsAppProductInfo(handle: Long, appIds: String, tokens: String): String?
        @JvmStatic private external fun nativeGetPicsPackageInfo(handle: Long, packageIds: String, tokens: String): String?
        @JvmStatic private external fun nativeNotifyGamesPlayed(handle: Long, gamesJson: String, clientOsType: Int)
        @JvmStatic private external fun nativeKickPlayingSession(handle: Long, onlyStopGame: Boolean)
        @JvmStatic private external fun nativeIsPlayingBlocked(handle: Long): Boolean
        @JvmStatic private external fun nativeMarkPlayingBlocked(handle: Long)
        @JvmStatic private external fun nativeSetPersonaState(handle: Long, personaState: Int)
        @JvmStatic private external fun nativeSetPersonaName(handle: Long, name: String, personaState: Int)
        @JvmStatic private external fun nativeRequestUserPersona(handle: Long)
        @JvmStatic private external fun nativeRequestFriendPersonas(
            handle: Long, steamIds: LongArray, flags: Int)
        @JvmStatic private external fun nativeGetSelfPersona(handle: Long): String?
        @JvmStatic private external fun nativeGetFamilyGroup(
            handle: Long, familyGroupId: Long): String?
        @JvmStatic private external fun nativeGetLicenseList(handle: Long): String?
        @JvmStatic private external fun nativeGetFriendsList(handle: Long): LongArray
        @JvmStatic private external fun nativeGetFriendPersonas(handle: Long): String?
        @JvmStatic private external fun nativeGetOwnedGames(
            handle: Long, steamId: Long): String?
        @JvmStatic private external fun nativeSignalAppLaunchIntent(handle: Long, appId: Int, clientId: Long, machineName: String, ignorePending: Boolean, osType: Int): String?
        @JvmStatic private external fun nativeSignalAppExitSyncDone(handle: Long, appId: Int, clientId: Long, uploadsCompleted: Boolean, uploadsRequired: Boolean)
        @JvmStatic private external fun nativeGetLibrarySnapshot(handle: Long): String
        @JvmStatic private external fun nativeSetLibraryObserver(
            handle: Long,
            observer: WnLibraryObserver?,
        )
        @JvmStatic private external fun nativeState(handle: Long): Int
        @JvmStatic private external fun nativeSteamId(handle: Long): Long
        @JvmStatic private external fun nativeFamilyGroupId(handle: Long): Long
    }
}
