// IMPORTANT: the package path below is hard-bound to JNI symbol names in
// app/src/main/cpp/wn-steam-client/jni/wn_session_jni.cpp
// (Java_com_winlator_cmod_feature_stores_steam_wnsteam_WnSteamSession_*).
// Do NOT move this file or rename the package without updating that file.
package com.winlator.cmod.feature.stores.steam.wnsteam

import java.util.concurrent.atomic.AtomicLong

/**
 * Production-facing handle to a native CMClient + AuthSession pair.
 * Replaces JavaSteam's `SteamClient + SteamAuthentication` surface for
 * the login flow in Phase 2.
 *
 * Lifecycle:
 *   1. Construct (allocates a native handle).
 *   2. [setCaBundlePath] (required for TLS).
 *   3. [setStateObserver] before [connect].
 *   4. [connect] with a `wss://...:443/cmsocket/` URL.
 *   5. Wait for [WnSteamStateObserver.onStateChanged] reporting Connected (=2).
 *   6. [startLoginWithCredentials] or [startLoginWithQr], get a refresh token.
 *   7. [logonWithRefreshToken] — moves to LoggedOn (state 3).
 *   8. [close] when done.
 */
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

    /**
     * Enables/disables the post-logon library-populate PICS crawl. Call
     * before [logonWithRefreshToken]. A download-only session should pass
     * false: the crawl floods the CM with hundreds of PICS items right when
     * the download needs the channel for depot keys.
     */
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

    /**
     * Begins the credentials login flow:
     *   GetPasswordRSAPublicKey → encrypt password → BeginAuthSession →
     *   prompt the [authenticator] for Steam Guard codes as needed →
     *   PollAuthSessionStatus until a refresh token is issued.
     *
     * Calls [callback] with the final [WnAuthResult] on a native worker
     * thread. Marshal to your own dispatcher before touching UI state.
     */
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

    fun startLoginWithQr(
        qrCallback: WnQrCallback,
        resultCallback: WnAuthCallback,
    ) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeStartLoginWithQr(h, qrCallback, resultCallback)
    }

    /** Cancel any in-flight credentials or QR session. */
    fun cancelLogin() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeCancelLogin(h)
    }

    /**
     * Sends CMsgClientLogon with the given refresh token (the access_token
     * field on the wire — confusingly named). After this, the channel
     * transitions to LoggedOn and the heartbeat starts automatically.
     *
     * @param accountName Steam login username. REQUIRED — Steam rejects with
     * EResult.InvalidPassword if omitted, even when the refresh token is valid.
     * @param steamId optional client-supplied SteamID64 (0 = let CM resolve).
     * Returns false if the channel isn't yet in Connected state.
     */
    fun logonWithRefreshToken(refreshToken: String, accountName: String, steamId: Long = 0L): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeLogonWithRefreshToken(h, refreshToken, accountName, steamId)
    }

    /**
     * Pre-warm PICS metadata for [appId] and its [dlcAppIds] before launching
     * a game. Solves boot-time hangs in titles that block on subscription
     * validation (Vampire Survivors et al.) — by the time the game's first
     * ownership query fires, the data is already cached.
     *
     * The callback fires on a native worker thread; marshal to your own
     * dispatcher before touching UI. ok=true means every requested appid is
     * cached and ready; ok=false means the PICS round-trip failed (timeout,
     * disconnect, parse error) — game can still launch but may stall on
     * ownership checks.
     */
    fun prepareApp(appId: Int, dlcAppIds: IntArray, callback: WnPrepareAppCallback) {
        val h = nativeHandle.get()
        if (h == 0L) {
            callback.onPrepareResult(false, "session closed")
            return
        }
        nativePrepareApp(h, appId, dlcAppIds, callback)
    }

    /**
     * Download an app's depots with the C++ WN-Steam-Client (Phase 5) — the
     * drop-in replacement for JavaSteam's DepotDownloader. Returns
     * immediately; the actual download runs on a native worker thread and
     * reports back through [listener] (also on that thread — marshal to your
     * own dispatcher before touching UI or Room).
     *
     * @param depotIds    depots to download — parallel to [manifestIds]
     * @param manifestIds the target manifest "gid" for each depot
     * @param branch      manifest branch ("public" for the default)
     * @param installDir  absolute flat install dir (the game's app dir)
     * @param fresh       true when the install has no COMPLETE marker —
     *                    discards a stale depot.config so every depot is
     *                    re-validated; false enables per-depot resume.
     * @param caBundlePath PEM trust bundle for HTTPS CDN verification
     * @param maxWorkers   parallel chunk-download worker count (the user's
     *                     "Download Speed" setting — 8 / 16 / 24 / 32); the
     *                     native engine clamps it to [1, 64]
     */
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

    /**
     * Aborts the depot download started by [downloadApp]. The native worker
     * polls a cancel flag before each depot and each chunk fetch and unwinds
     * promptly — pause / cancel from the UI route here so the native thread
     * actually stops instead of running on in the background. Non-blocking;
     * a no-op when no download is running.
     */
    fun cancelDownload() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeCancelDownload(h)
    }

    /**
     * Start the Wine bridge — the Steam3Master + SteamClientService TCP
     * listeners Proton's lsteamclient.dll connects to. Bind ports default
     * to the Valve canonical 57343/57344; pass 0 for either to keep the
     * default. Returns false if the bind fails (port already in use, etc.);
     * inspect [wineBridgeLastError] for the reason.
     *
     * Phase 8b.1: the bridge accepts connections and logs the first 64
     * bytes of each frame for diagnostic purposes. Phase 8b.2 will hold
     * connections open and proxy frames through libsteamclient.so.
     */
    fun startWineBridge(steam3Port: Int = 0, clientServicePort: Int = 0): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeStartWineBridge(h, steam3Port, clientServicePort)
    }

    /** Stop the Wine bridge listeners. Idempotent. */
    fun stopWineBridge() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeStopWineBridge(h)
    }

    fun wineBridgeLastError(): String {
        val h = nativeHandle.get(); if (h == 0L) return ""
        return nativeWineBridgeLastError(h)
    }

    /**
     * Returns the cached app ownership ticket for [appId], or null when not
     * pre-warmed yet. The blob is opaque, signed by Valve, and is what
     * Wine's lsteamclient.dll hands to game code on
     * SteamUser()->GetAppOwnershipTicket(appid). Pre-warm by calling
     * [prepareApp] before launching a game.
     *
     * This is a synchronous local-cache read — no network round-trip — so
     * it's safe to call from the Wine IPC hot path in Phase 8b.
     */
    fun getAppOwnershipTicket(appId: Int): ByteArray? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetAppOwnershipTicket(h, appId)
    }

    /**
     * Request a fresh encrypted app ticket (RequestEncryptedAppTicket).
     * Returns the serialized EncryptedAppTicket protobuf bytes — base64 of
     * these is what Goldberg's steam_settings/configs.user.ini `ticket=`
     * expects. BLOCKING (CM round-trip, ~up to 30s) — call off the main
     * thread. null on failure / not logged on.
     */
    fun requestEncryptedAppTicket(appId: Int): ByteArray? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeRequestEncryptedAppTicket(h, appId)
    }

    /**
     * Request the app's user-stats / achievement schema
     * (CMsgClientGetUserStats). Returns the binary-VDF UserGameStatsSchema
     * bytes — [StatsAchievementsGenerator] turns these into Goldberg's
     * steam_settings/achievements.json + stats.json. BLOCKING (CM
     * round-trip, ~up to 30s) — call off the main thread. null when the app
     * has no stats schema / not logged on / on failure.
     */
    fun getUserStatsSchema(appId: Int): ByteArray? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetUserStatsSchema(h, appId)
    }

    /**
     * Request the app's full user-stats response (CMsgClientGetUserStats):
     * JSON `{"eresult","crcStats","schema":"<hex>","achievementBlocks":
     * [{"achievementId","unlockTimes":[...]}]}`. Used by the achievement
     * write-back path, which needs `crcStats` + `achievementBlocks` on top of
     * the schema. BLOCKING (CM round-trip, ~up to 30s). null on failure.
     */
    fun getUserStatsFull(appId: Int): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetUserStatsFull(h, appId)
    }

    /**
     * Fetch the app's Steam Inventory item-definition archive. Two-step:
     * Inventory.GetItemDefMeta#1 (CM) → digest, then a keyless HTTPS GET of
     * IGameInventory/GetItemDefArchive/v1. Returns the raw archive JSON (a
     * JSON array of item definitions) decoded as UTF-8 — [InventoryItemsGenerator]
     * pivots it into Goldberg's steam_settings/items.json. BLOCKING (CM
     * round-trip + HTTP) — call off the main thread. null when the app has no
     * inventory / not logged on / on transport failure.
     *
     * @param caBundlePath PEM trust bundle for the HTTPS GetItemDefArchive GET
     */
    fun getItemDefArchive(appId: Int, caBundlePath: String): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        val bytes = nativeGetItemDefArchive(h, appId, caBundlePath) ?: return null
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Fetch the signed-in account's subscribed Steam Workshop items for [appId]
     * via PublishedFile.GetUserFiles#1 (type="mysubscriptions"). Returns a JSON
     * array string — one object per item with publishedFileId, appId, title,
     * fileName, fileUrl, previewUrl, fileSizeBytes, hcontentFile, timeUpdated.
     * BLOCKING (paged CM round-trips) — call off the main thread. Returns "[]"
     * when the account has no subscriptions; null on transport failure / not
     * logged on.
     */
    fun getSubscribedWorkshopItems(appId: Int): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetSubscribedWorkshopItems(h, appId)
    }

    /**
     * Download one Steam Workshop item's content into [installDir]. The item's
     * content is a SteamPipe depot, so this reuses the full depot pipeline;
     * [manifestId] is the item's `hcontent_file`. BLOCKING (CM round-trips +
     * CDN chunk transfer) — call off the main thread. Returns the bytes
     * written, or -1 on failure / not logged on.
     */
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

    /**
     * Write stat / achievement values back to Steam (CMsgClientStoreUserStats2).
     * Fire-and-forget. [statIds] and [statValues] are paired and must be the
     * same length. [crcStats] must come from the matching [getUserStatsFull].
     */
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

    /**
     * List the app's remote Steam Cloud save files (Cloud.GetAppFileChangelist).
     * Returns a JSON object string:
     * `{"currentChangeNumber":N,"pathPrefixes":[...],"machineNames":[...],
     *   "files":[{"fileName","sha","timestamp","size","persistState",
     *             "pathPrefixIndex","machineNameIndex"}, ...]}`
     * — the foundation of the Phase 6 cloud-restore path. BLOCKING (CM
     * round-trip, ~up to 30s) — call off the main thread. null on failure /
     * not logged on.
     */
    fun getCloudFileList(appId: Int): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetCloudFileList(h, appId)
    }

    /**
     * Resolve the HTTP(S) download location of a remote cloud-save file
     * (Cloud.ClientFileDownload). Returns a JSON object string:
     * `{"fileSize","rawFileSize","sha","timestamp","urlHost","urlPath",
     *   "useHttps","encrypted","headers":[{"name","value"}, ...]}`.
     * BLOCKING (CM round-trip) — call off the main thread. null on failure.
     */
    fun getCloudDownloadInfo(appId: Int, filename: String): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetCloudDownloadInfo(h, appId, filename)
    }

    /**
     * Download a remote Steam Cloud save file's final body. Resolves the
     * location via [getCloudDownloadInfo], performs the HTTP(S) GET
     * (replaying the server-supplied request headers), and — when Steam
     * served the file compressed (`fileSize != rawFileSize`) — extracts the
     * single zip entry, so the caller always gets ready-to-write bytes.
     * BLOCKING — call off the main thread. null on failure.
     */
    fun downloadCloudFile(appId: Int, filename: String): ByteArray? {
        val infoJson = getCloudDownloadInfo(appId, filename) ?: return null
        return try {
            val obj = org.json.JSONObject(infoJson)
            val host = obj.optString("urlHost")
            if (host.isEmpty()) return null
            val fileSize = obj.optInt("fileSize", 0)
            val rawFileSize = obj.optInt("rawFileSize", 0)
            val scheme = if (obj.optBoolean("useHttps", false)) "https" else "http"
            val url = java.net.URL("$scheme://$host${obj.optString("urlPath")}")
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
            // fileSize != rawFileSize → Steam served a single-entry zip.
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
            // Reject a short/truncated body before the caller can write it:
            // rawFileSize is the file's true (decompressed) size, and a
            // truncated HTTP read returns partial bytes without throwing.
            // A partial save must never atomically replace a good local one.
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

    /** Result of [beginCloudUploadBatch]: the batch id + the app's change number. */
    data class CloudUploadBatch(val batchId: Long, val appChangeNumber: Long)

    /**
     * Open a Steam Cloud upload batch (Cloud.BeginAppUploadBatch). Pass the
     * remote names of every file the batch will upload and delete. Returns
     * the batch id (to thread through [uploadCloudFile] /
     * [completeCloudUploadBatch]) plus the app change number, or null on
     * failure. BLOCKING — call off the main thread.
     */
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

    /**
     * Upload one file's body to Steam Cloud within an open batch
     * (Cloud.ClientBeginFileUpload → HTTP PUT blocks → ClientCommitFileUpload).
     * `fileShaHex` is the lowercase-hex SHA-1 of `fileBytes`. Returns true if
     * the server committed the file. BLOCKING — call off the main thread.
     */
    fun uploadCloudFile(
        appId: Int,
        filename: String,
        fileBytes: ByteArray,
        fileShaHex: String,
        timestamp: Long,
        batchId: Long,
    ): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        // Cloud uploads are not compressed — file_size == raw_file_size.
        val beginJson = nativeCloudBeginFileUpload(
            h, appId, filename, fileBytes.size, fileBytes.size, fileShaHex, timestamp, batchId,
        ) ?: return false
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
                    val slice = fileBytes.copyOfRange(off, off + len)
                    val scheme = if (blk.optBoolean("useHttps", false)) "https" else "http"
                    val url = java.net.URL("$scheme://$host${blk.optString("urlPath")}")
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "PUT"
                        doOutput = true
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        setFixedLengthStreamingMode(slice.size)
                        blk.optJSONArray("headers")?.let { hs ->
                            for (k in 0 until hs.length()) {
                                val hd = hs.getJSONObject(k)
                                setRequestProperty(hd.optString("name"), hd.optString("value"))
                            }
                        }
                        setRequestProperty("User-Agent", "Valve/Steam HTTP Client 1.0")
                    }
                    try {
                        conn.outputStream.use { it.write(slice) }
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

    /**
     * Close a Steam Cloud upload batch (Cloud.CompleteAppUploadBatchBlocking).
     * `batchEresult` is 1 (OK) when every file uploaded, else a failure code.
     * BLOCKING — call off the main thread.
     */
    fun completeCloudUploadBatch(appId: Int, batchId: Long, batchEresult: Int): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeCloudCompleteUploadBatch(h, appId, batchId, batchEresult)
    }

    /**
     * Poll PICS for everything that changed since [sinceChangeNumber]
     * (CMsgClientPICSChangesSince). Pass 0 on first call. Returns a JSON
     * object string:
     * `{"currentChangeNumber":N,"forceFullUpdate":bool,
     *   "apps":[{"appid","changeNumber","needsToken"}],"packages":[...]}`
     * — the app-metadata refresh loop re-fetches the listed apps via PICS
     * product info. BLOCKING — call off the main thread. null on failure.
     */
    fun getPicsChangesSince(sinceChangeNumber: Long): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetPicsChangesSince(h, sinceChangeNumber)
    }

    /**
     * Fetch one app's PICS product info (CMsgClientPICSProductInfo). The C++
     * side parses the appinfo VDF (text or binary) and returns a JSON object
     * string `{"changeNumber":N,"appinfo":{...}}` — feed the `appinfo` object
     * to [WnKeyValue.fromJsonObject] then [generateSteamApp]. [accessToken]
     * is 0 for public appinfo (the common case); pass a token from
     * [getPicsChangesSince]'s `needsToken` apps. BLOCKING — call off the main
     * thread. null on failure / unknown app.
     */
    fun getPicsAppInfo(appId: Int, accessToken: Long = 0L): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetPicsAppInfo(h, appId, accessToken)
    }

    /**
     * Request PICS access tokens for [appIds] and [packageIds]. Returns JSON
     * `{"appTokens":{"<id>":"<token>"},"packageTokens":{...}}` (tokens are
     * uint64 strings). BLOCKING — call off the main thread. null on failure.
     */
    fun getPicsAccessTokens(appIds: List<Int>, packageIds: List<Int>): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetPicsAccessTokens(
            h, appIds.joinToString("\n"), packageIds.joinToString("\n"),
        )
    }

    /**
     * Batch PICS app product-info. [tokens] is parallel to [appIds] (0 for
     * public). Returns a JSON array
     * `[{"appid":N,"changeNumber":N,"appinfo":{...}}, ...]`. BLOCKING. null
     * on failure.
     */
    fun getPicsAppProductInfo(appIds: List<Int>, tokens: List<Long>): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetPicsAppProductInfo(
            h, appIds.joinToString("\n"), tokens.joinToString("\n"),
        )
    }

    /**
     * Batch PICS package product-info. [tokens] is parallel to [packageIds].
     * Returns a JSON array
     * `[{"packageid":N,"changeNumber":N,"appids":[...],"depotids":[...]}, ...]`.
     * BLOCKING. null on failure.
     */
    fun getPicsPackageInfo(packageIds: List<Int>, tokens: List<Long>): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetPicsPackageInfo(
            h, packageIds.joinToString("\n"), tokens.joinToString("\n"),
        )
    }

    /**
     * Report the client's running games (CMsgClientGamesPlayed) for friends
     * "now playing" presence + playtime. [gamesJson] is a JSON array
     * `[{"gameId","processId","ownerId","launchSource","gameBuildId",
     *   "processes":[{"pid","ppid","isSteam"}]}]`. Fire-and-forget.
     */
    fun notifyGamesPlayed(gamesJson: String, clientOsType: Int) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeNotifyGamesPlayed(h, gamesJson, clientOsType)
    }

    /**
     * Release this account's other active playing session
     * (CMsgClientKickPlayingSession) so this client can take over.
     * Fire-and-forget.
     */
    fun kickPlayingSession(onlyStopGame: Boolean = false) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeKickPlayingSession(h, onlyStopGame)
    }

    /**
     * Whether playing is currently blocked for this account (another
     * logged-on session holds the playing slot). Reads the cached
     * server-pushed CMsgClientPlayingSessionState; non-blocking.
     */
    fun isPlayingBlocked(): Boolean {
        val h = nativeHandle.get(); if (h == 0L) return false
        return nativeIsPlayingBlocked(h)
    }

    /**
     * Force the playing-blocked cache to true. Call right before
     * [kickPlayingSession] so the kick's wait-loop only observes a
     * post-kick server push, never a stale value from an earlier cycle.
     */
    fun markPlayingBlocked() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeMarkPlayingBlocked(h)
    }

    /**
     * Publish persona state (CMsgClientChangeStatus) so Steam friends see
     * online/offline. [personaState] is an EPersonaState code (0 Offline,
     * 1 Online, …). Fire-and-forget.
     */
    fun setPersonaState(personaState: Int) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSetPersonaState(h, personaState)
    }

    /**
     * Request the local user's persona data (CMsgClientRequestFriendData).
     * The reply is server-pushed and cached — poll [getSelfPersona] after.
     */
    fun requestUserPersona() {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeRequestUserPersona(h)
    }

    /**
     * The local user's cached persona as JSON
     * `{"personaState","gameAppId","playerName","avatarHash","gameName","gameId"}`,
     * or null if no CMsgClientPersonaState has arrived yet.
     */
    fun getSelfPersona(): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetSelfPersona(h)
    }

    /**
     * Enumerate the local user's Steam Family (FamilyGroups.GetFamilyGroup#1).
     * Blocks until the reply arrives (30s native timeout). Returns the group
     * as JSON `{"name","members":[steamid64,...]}`, or null on failure / not
     * logged on.
     */
    fun getFamilyGroup(familyGroupId: Long): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetFamilyGroup(h, familyGroupId)
    }

    /**
     * The cached CMsgClientLicenseList as a JSON array — one object per
     * license with all fields the `steam_license` Room table needs. Empty
     * array until the post-logon license push has arrived; null only on a
     * dead handle. Non-blocking.
     */
    fun getLicenseList(): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetLicenseList(h)
    }

    /**
     * List the games a user owns (Player.GetOwnedGames#1). Blocks until the
     * reply arrives (30s native timeout). Returns a JSON array
     * `[{appId,name,playtimeTwoWeeks,playtimeForever,imgIconUrl,sortAs,
     * rtimeLastPlayed},...]` (`[]` for a private library), or null on
     * failure / not logged on.
     */
    fun getOwnedGames(steamId: Long): String? {
        val h = nativeHandle.get(); if (h == 0L) return null
        return nativeGetOwnedGames(h, steamId)
    }

    /**
     * Signal Steam that an app is launching (Cloud.SignalAppLaunchIntent).
     * Returns the list of pending-remote-operation codes — empty list = clear
     * to launch; codes: 1 AppSessionActive, 2 UploadInProgress, 3 UploadPending,
     * 4 AppSessionSuspended. null on failure. BLOCKING — call off the main thread.
     */
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

    /**
     * Signal Steam that cloud sync finished on app exit
     * (Cloud.SignalAppExitSyncDone). Fire-and-forget.
     */
    fun signalAppExitSyncDone(
        appId: Int,
        clientId: Long,
        uploadsCompleted: Boolean,
        uploadsRequired: Boolean,
    ) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSignalAppExitSyncDone(h, appId, clientId, uploadsCompleted, uploadsRequired)
    }

    /**
     * Fetch a fresh JSON snapshot of the native library store. Cheap enough
     * to call eagerly per change observation; for typed access use
     * [WnLibraryStore].
     */
    fun getLibrarySnapshotJson(): String {
        val h = nativeHandle.get(); if (h == 0L) return "{}"
        return nativeGetLibrarySnapshot(h)
    }

    /**
     * Install (or remove with null) a callback that fires whenever the native
     * library store mutates (license list ingested, PICS responses processed,
     * access tokens granted).
     */
    fun setLibraryObserver(observer: WnLibraryObserver?) {
        val h = nativeHandle.get(); if (h == 0L) return
        nativeSetLibraryObserver(h, observer)
    }

    /** Current native ClientState (0..3). */
    fun state(): Int {
        val h = nativeHandle.get(); if (h == 0L) return 0
        return nativeState(h)
    }

    /** SteamID64 after successful logon, or 0 if not logged on. */
    fun steamId(): Long {
        val h = nativeHandle.get(); if (h == 0L) return 0L
        return nativeSteamId(h)
    }

    /**
     * The Steam Family group id from the logon response, or 0 if the account
     * is not in a family (or not logged on yet). Feed a non-zero value to
     * [getFamilyGroup].
     */
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
        /**
         * Synchronously resolves a WSS URL for a Steam CM. Uses Steam
         * Directory (`GetCMListForConnect`) with a hardcoded SteamKit-style
         * fallback list if the directory is unreachable. Caller must be on
         * a background dispatcher — this blocks on a curl HTTPS call.
         *
         * @param caBundlePath absolute path to a single-file PEM trust
         * bundle (typically from CaBundleExtractor.ensureBundle). Empty
         * string disables TLS verification source — the call will then
         * fail because verifypeer is on.
         *
         * @return WSS URL or empty string on total failure.
         */
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
        @JvmStatic private external fun nativeRequestUserPersona(handle: Long)
        @JvmStatic private external fun nativeGetSelfPersona(handle: Long): String?
        @JvmStatic private external fun nativeGetFamilyGroup(
            handle: Long, familyGroupId: Long): String?
        @JvmStatic private external fun nativeGetLicenseList(handle: Long): String?
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
