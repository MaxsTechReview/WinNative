package com.winlator.cmod.feature.stores.steam.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.feature.stores.steam.wnsteam.WnSteamBootstrap

class HybridModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val op = intent.getStringExtra("op")?.lowercase() ?: "query"
        val before = PrefManager.wnHybridMode
        val bsInitialized = WnSteamBootstrap.isLoggedOn() ||
            WnSteamBootstrap.steamId() != 0L

        val after: Boolean = when (op) {
            "enable"  -> true
            "disable" -> false
            "toggle"  -> !before
            "query"   -> before
            "state"   -> before    // verbose query, no mutation
            "forceinit" -> before  // fires WnSteamBootstrap.prewarm directly,
            "seedteststats" -> before  // pushes a synthetic schema into
            "seedtestfriends" -> before  // pushes 2 synthetic friend
            "seedtestrichpresence" -> before  // exercises ISteamFriends RP
            "seedtestoverlay" -> before  // cycles setGameOverlayActive
            "probebridge" -> before      // calls WnLibSteamClient.setPersonaState
            "probebridgename" -> before  // calls ISteamFriends.SetPersonaName
            "probebridgereq" -> before   // calls ISteamFriends.RequestUserInformation
            "probebridgereqbulk" -> before  // drives cm_bridge bulk variant for an
            "probebridgeticket" -> before   // probes cm_bridge's cached-
            "probeauthwrap" -> before       // injects synthetic ownership bytes,
            "probebridgeplayed" -> before   // toggles setAppId 0→N→0 to exercise
            "probebridgerp" -> before       // calls ISteamFriends.RequestFriendRich
            "probebridgeclearrp" -> before  // calls SetRichPresence then
            "probebridgelogon" -> before    // synthetically dispatches the cm_
            "probebridgefriends" -> before  // synthetically dispatches the cm_
            "probebridgelicenses" -> before // injects 3 licenses (1 self-owned,
            "probepurchasetime" -> before   // pushes synthetic licenses +
            "probefamilyshare" -> before    // pushes self-owned + family-shared
            "probeappowner" -> before       // verifies slot 20 GetAppOwner
            "probeavgrate" -> before        // calls UpdateAvgRateStat (slot 5)
            "probetimedtrial" -> before     // injects a trial license (90 min
            "probedlcinstalled" -> before   // verifies slot 7 contract across
            "probebetaname" -> before       // pushes a beta branch via
            "probedlprogress" -> before     // pushes (1234, 5000) via
            "probecloudio" -> before        // points the bound app's cloud
            "probecloudasync" -> before     // FileWriteAsync (slot 2) +
            "probecloudstream" -> before    // FileWriteStream{Open,WriteChunk,
            "probeappsbool" -> before       // ISteamApps slots 0/1/2/3 vs
            "probefilestate" -> before      // FileForget (5) + FilePersisted
            "probeuserbool" -> before       // ISteamUser slots 26-29 (phone/
            "probeaccountinfo" -> before    // cm_bridge account-info observer
            "probeuserdata" -> before       // ISteamUser slot 6 GetUserData
            "probefriendrel" -> before      // ISteamFriends slots 5 + 17:
            "probewebapiticket" -> before   // ISteamUser slot 14: synthetic
            "probelicense" -> before        // ISteamUser slot 18: HasLicense
            "probecorrupt" -> before        // ISteamApps slot 16: marks the
            "probefriendlevel" -> before    // ISteamFriends slot 10:
            "probeplayerlevel" -> before    // ISteamUser slots 23/24/25/30/31
            "probecloudshare" -> before     // ISteamRemoteStorage slot 7
            "probenicksig" -> before        // ISteamFriends 11 GetPlayer
            "probebridgepersona" -> before  // synthetically dispatches the cm_
            "probebridgepersonaself" -> before  // same as probebridgepersona but
            "seedtestavatar" -> before   // pushes a synthetic 4×4 RGBA8
            "seedtestavatarhash" -> before  // pushes a synthetic 20-byte
            "seedtestfetchavatar" -> before  // fetches a known-stable avatar
            "seedtestselfavatar" -> before   // sets a synthetic self
            "seedtestprefswarmup" -> before  // seeds PrefManager with a
            "seedtestfriendsnapshot" -> before  // pushes a 2-friend JSON
            "seedtestschemacache" -> before     // writes a synthetic per-app
            "seedtestavatartiers" -> before     // calls enqueueAllTiers, polls
            "togglelogon"   -> before    // cycles setLoggedOn(false)→
            "seedtestquota" -> before    // pushes a synthetic 5 GB/5 GB
            "seedtestcloud" -> before    // pushes 2 synthetic cloud
            "probelocale"  -> before     // toggles pushed ui_language
            "probeshutdown" -> before    // cycles the pipe (Create→
            "probelogonfail" -> before   // reports a synthetic
            "proberundispatch" -> before // registers a probe cb on
            "probecallresult" -> before  // pushes a synthetic CCallResult
            "probeauthticket" -> before  // calls ISteamUser.GetAuthSessionTicket
            "probeencticket" -> before   // calls ISteamUser.RequestEncryptedApp
            "probeutilsapicall" -> before // pushes a synthetic CallResult,
            "probedlc"        -> before  // pushes 3 synthetic DLC entries
            "probedepots"     -> before  // pushes 4 synthetic depot ids
            "probebuildid"    -> before  // pushes a synthetic buildId,
            "probepreparelaunch" -> before  // runs the full launch-time
            "probelogongate" -> before  // engages the Kotlin-side rate-
            "probelogongateclear" -> before  // clears the rate-limit
            else      -> {
                Log.w(TAG, "Unknown op=$op; valid: enable/disable/toggle/query/state/forceInit/seedTestStats")
                return
            }
        }

        if (op == "forceinit") {
            try {
                WnSteamBootstrap.prewarm(context.applicationContext)
                Log.i(TAG, "forceInit: prewarm dispatched (will attempt logon w/ existing token)")
            } catch (t: Throwable) {
                Log.e(TAG, "forceInit: prewarm threw", t)
            }
        } else if (op == "probebuildid") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val bs = WnSteamBootstrap
                W.setAppId(440)
                W.setAppBuildId(440, 0x12345678)
                val got = bs.appBuildId()
                Log.i(TAG, "probeBuildId: appBuildId(440)=$got " +
                    "(expect 305419896 = 0x12345678)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBuildId threw", t)
            }
        } else if (op == "probelogongate") {
            try {
                val SS = com.winlator.cmod.feature.stores.steam.service.SteamService
                val eresult = intent.getIntExtra("eresult", 84)
                val beforeUntil = SS.logonGateUntilMs
                val beforeFails = SS.consecutiveLogonFailures
                SS.recordLogonFailure(eresult)
                val afterUntil = SS.logonGateUntilMs
                val afterFails = SS.consecutiveLogonFailures
                val nowMs = System.currentTimeMillis()
                val backoffS = ((afterUntil - nowMs).coerceAtLeast(0L) / 1000L)
                Log.i(TAG, "probeLogonGate: synth EResult=$eresult — " +
                    "gate before=$beforeUntil after=$afterUntil (in ${backoffS}s) " +
                    "consecutive=$beforeFails→$afterFails")
            } catch (t: Throwable) {
                Log.e(TAG, "probeLogonGate threw", t)
            }
        } else if (op == "probelogongateclear") {
            try {
                val SS = com.winlator.cmod.feature.stores.steam.service.SteamService
                val beforeUntil = SS.logonGateUntilMs
                val beforeFails = SS.consecutiveLogonFailures
                SS.recordLogonSuccess()
                Log.i(TAG, "probeLogonGateClear: gate ${beforeUntil}→${SS.logonGateUntilMs} " +
                    "consecutive=$beforeFails→${SS.consecutiveLogonFailures}")
            } catch (t: Throwable) {
                Log.e(TAG, "probeLogonGateClear threw", t)
            }
        } else if (op == "probepreparelaunch") {
            try {
                val appId = intent.getIntExtra("appid", 242760)
                val bs = WnSteamBootstrap
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                Log.i(TAG, "probePrepareLaunch: starting chokepoint for app=$appId")
                Thread({
                    try {
                        com.winlator.cmod.feature.stores.steam.service.SteamService
                            .prepareLibSteamClientForLaunchBlocking(appId)
                        val q = bs.cloudQuota()
                        val total = if (q.isNotEmpty()) q[0] else 0L
                        val avail = if (q.size >= 2) q[1] else 0L
                        val etk = runCatching {
                            W.nativeGetPushedEncryptedAppTicketSize(appId)
                        }.getOrNull() ?: 0
                        val pAppId    = runCatching { W.nativeGetPushedAppId() }.getOrNull() ?: 0
                        val pCloudApp = runCatching { W.nativeGetPushedCloudEnabledApp() }.getOrNull() ?: false
                        val pCFiles   = runCatching { W.nativeGetPushedCloudFileCount() }.getOrNull() ?: 0
                        Log.i(TAG, "probePrepareLaunch: app=$appId DONE — " +
                            "boundAppId=${bs.currentAppId()} " +
                            "subscribed=${bs.isSubscribedApp(appId)} " +
                            "cloudApp=${bs.cloudEnabledForApp()} " +
                            "cloudFiles=${bs.cloudFileCount()} " +
                            "cloudQuota=$total/$avail " +
                            "encTicketBytes=$etk " +
                            "(pushed: appId=$pAppId cloudApp=$pCloudApp cloudFiles=$pCFiles)")
                    } catch (t: Throwable) {
                        Log.e(TAG, "probePrepareLaunch: chokepoint threw", t)
                    }
                }, "probePrepareLaunch").start()
            } catch (t: Throwable) {
                Log.e(TAG, "probePrepareLaunch threw", t)
            }
        } else if (op == "probedepots") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val bs = WnSteamBootstrap
                W.setAppInstalledDepots(440, intArrayOf(441, 442, 443, 444))
                val got = bs.installedDepots(440)
                Log.i(TAG, "probeDepots: installedDepots(440)=${got.toList()} " +
                    "(expect [441, 442, 443, 444])")
            } catch (t: Throwable) {
                Log.e(TAG, "probeDepots threw", t)
            }
        } else if (op == "probedlc") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val bs = WnSteamBootstrap
                W.setAppDlcs(
                    parentAppId = 440,
                    dlcAppIds   = intArrayOf(440001, 440002, 440003),
                    dlcNames    = arrayOf("Demo Pack", "Heavy Pack", "Engineer Pack"),
                    available   = booleanArrayOf(true, true, false),
                )
                val count = bs.dlcCount(440)
                Log.i(TAG, "probeDlc: GetDLCCount(440)=$count (expect 3)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeDlc threw", t)
            }
        } else if (op == "probeutilsapicall") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val expected = 0xCAFEBABE.toInt()
                val observed = try {
                    W.nativeDiagnosticUtilsGetAPICallResult(callbackId = 1101, eresult = expected)
                } catch (_: UnsatisfiedLinkError) { 0 }
                Log.i(TAG, "probeUtilsAPICall: observed=0x" +
                    Integer.toHexString(observed) +
                    " (expect 0x" + Integer.toHexString(expected) + ")")
            } catch (t: Throwable) {
                Log.e(TAG, "probeUtilsAPICall threw", t)
            }
        } else if (op == "probeencticket") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val bs = WnSteamBootstrap
                W.setAppId(440)
                val (hCall, body) = bs.diagnosticRequestEncryptedAppTicket()
                val magic = if (body.size >= 6) String(body, 0, 6, Charsets.US_ASCII) else "?"
                val embeddedApp = if (body.size >= 20)
                    (body[16].toInt() and 0xFF) or
                    ((body[17].toInt() and 0xFF) shl 8) or
                    ((body[18].toInt() and 0xFF) shl 16) or
                    ((body[19].toInt() and 0xFF) shl 24) else -1
                Log.i(TAG, "probeEncTicket: hCall=$hCall body=${body.size}B " +
                    "magic='$magic' embeddedApp=$embeddedApp " +
                    "(expect hCall>=1, magic='WNETKT', embeddedApp=440)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeEncTicket threw", t)
            }
        } else if (op == "probeauthticket") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val buf = ByteArray(64)
                val pre = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val handle = try { W.nativeDiagnosticGetAuthTicket(buf) }
                             catch (_: UnsatisfiedLinkError) { 0 }
                val post = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val magic = String(buf, 0, 4, Charsets.US_ASCII)
                val handleInBuf =
                    (buf[4].toInt() and 0xFF) or
                    ((buf[5].toInt() and 0xFF) shl 8) or
                    ((buf[6].toInt() and 0xFF) shl 16) or
                    ((buf[7].toInt() and 0xFF) shl 24)
                Log.i(TAG, "probeAuthTicket: handle=$handle magic='$magic' " +
                    "embeddedHandle=$handleInBuf cbDepth pre=$pre post=$post " +
                    "(expect handle>=1 magic='WNAT' embeddedHandle==handle " +
                    "post=pre+1 [GetAuthSessionTicketResponse_t emitted])")
            } catch (t: Throwable) {
                Log.e(TAG, "probeAuthTicket threw", t)
            }
        } else if (op == "probecallresult") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val expectedEresult = 0xABCD
                val packed = try {
                    W.nativeDiagnosticPushAndDrainCallResult(
                        callbackId = 1101, eresult = expectedEresult)
                } catch (_: UnsatisfiedLinkError) { 0L }
                val runs   = (packed ushr 32).toInt()
                val observed = (packed and 0xFFFFFFFFL).toInt()
                Log.i(TAG, "probeCallResult: runs=$runs observedEresult=0x" +
                    Integer.toHexString(observed) +
                    " (expect runs=1 observedEresult=0x" +
                    Integer.toHexString(expectedEresult) + ")")
            } catch (t: Throwable) {
                Log.e(TAG, "probeCallResult threw", t)
            }
        } else if (op == "proberundispatch") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                W.setAchievementSchema(
                    apiNames     = arrayOf("ACH_TEST_FIRST"),
                    displayNames = arrayOf("First Blood"),
                    descriptions = arrayOf("Score one kill."),
                    icons        = arrayOf(""),
                    hidden       = booleanArrayOf(false),
                )
                val pre = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val runs = try { W.nativeDiagnosticRegisterAndDrain(1101) }
                           catch (_: UnsatisfiedLinkError) { -1 }
                val post = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "probeRunDispatch: cb depth pre=$pre runs=$runs post=$post " +
                    "(expect runs>=1 — UserStatsReceived_t emitted by schema push)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeRunDispatch threw", t)
            }
        } else if (op == "probelogonfail") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val pre = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.reportLogonFailure(eresult = 84, stillRetrying = true)
                val post = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "probeLogonFail: cb depth pre=$pre post=$post " +
                    "(expect +1 SteamServerConnectFailure_t eresult=84/RateLimit)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeLogonFail threw", t)
            }
        } else if (op == "probeshutdown") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                W.setLoggedOn(true)
                val pre = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.shutdownPipe()
                val post = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "probeShutdown: cb depth pre=$pre post=$post " +
                    "(expect +1 SteamShutdown_t, +1 SteamServersDisconnected_t)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeShutdown threw", t)
            }
        } else if (op == "probelocale") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val bs = WnSteamBootstrap
                fun displayName(): String? =
                    bs.listAchievementsFull()
                        .firstOrNull { it.apiName == "ACH_TEST_FIRST" }
                        ?.displayName
                W.setUiLanguage("english")
                val en = displayName()
                W.setUiLanguage("spanish")
                val es = displayName()
                val gameLangEs = bs.currentGameLanguage()
                W.setUiLanguage(PrefManager.containerLanguage.ifBlank { "english" })
                val gameLangAfter = bs.currentGameLanguage()
                Log.i(TAG, "probeLocale: ACH_TEST_FIRST en='$en' es='$es' " +
                    "ISteamApps.lang(spanish)='$gameLangEs' " +
                    "ISteamApps.lang(restored)='$gameLangAfter' " +
                    "(expect en='First Blood' es='Primera Sangre' " +
                    "ISteamApps.lang(spanish)='spanish')")
            } catch (t: Throwable) {
                Log.e(TAG, "probeLocale threw", t)
            }
        } else if (op == "seedtestcloud") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                W.setAppId(440)  // TF2's appid as a stable test value
                val pre = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.setCloudFiles(
                    names      = arrayOf("save0.dat", "config.cfg"),
                    sizes      = intArrayOf(1024, 256),
                    timestamps = longArrayOf(1700000000L, 1700001000L),
                )
                val post = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "seedTestCloud: pushed 2 cloud files for app 440 " +
                    "(cb depth pre=$pre post=$post — expect +1 RemoteStorageAppSyncedClient_t)")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestCloud threw", t)
            }
        } else if (op == "seedtestquota") {
            try {
                val totalBytes = 5L * 1024L * 1024L * 1024L
                com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                    .setCloudQuota(totalBytes, totalBytes)
                Log.i(TAG, "seedTestQuota: pushed 5 GiB / 5 GiB available to libsteamclient.so")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestQuota threw", t)
            }
        } else if (op == "togglelogon") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val pre = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.setLoggedOn(true)
                val sameValue = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.setLoggedOn(false)
                val afterOff = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.setLoggedOn(true)
                val afterOn = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "toggleLogon: pre=$pre sameValue=$sameValue " +
                    "afterOff=$afterOff afterOn=$afterOn " +
                    "(expect pre==sameValue, +1 disconnected, +1 connected)")
            } catch (t: Throwable) {
                Log.e(TAG, "toggleLogon threw", t)
            }
        } else if (op == "seedtestfriends") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                W.setPersonaName("Voyager-1986#2")
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.setFriendsList(longArrayOf(76561197960287930L, 76561197960265728L))
                W.setFriendPersonaName(76561197960287930L, "Alice (test)")
                W.setFriendPersonaState(76561197960287930L, 1)         // Online
                W.setFriendGamePlayed(76561197960287930L, 440)         // playing TF2
                W.setFriendPersonaName(76561197960265728L, "Bob (test)")
                W.setFriendPersonaState(76561197960265728L, 0)         // Offline
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val aliceState = try {
                    W.nativeDiagnosticGetFriendPersonaState(76561197960287930L)
                } catch (_: UnsatisfiedLinkError) { -2 }
                val bobState = try {
                    W.nativeDiagnosticGetFriendPersonaState(76561197960265728L)
                } catch (_: UnsatisfiedLinkError) { -2 }
                Log.i(TAG, "seedTestFriends: pushed self-name + 2 friend personas " +
                    "(cb depth pre=$depth0 post=$depth1) " +
                    "aliceState=$aliceState bobState=$bobState " +
                    "(expect aliceState=1 bobState=0; cb depth Δ now includes " +
                    "the ComeOnline state-transition emit for Alice)")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestFriends threw", t)
            }
        } else if (op == "seedtestrichpresence") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val selfId = 0x110000100C123456L
                W.setSteamId(selfId)
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val setOk = try {
                    W.nativeDiagnosticSetRichPresence("status", "In Lobby")
                } catch (_: UnsatisfiedLinkError) { false }
                val setOk2 = try {
                    W.nativeDiagnosticSetRichPresence("connect", "+lobby:42")
                } catch (_: UnsatisfiedLinkError) { false }
                val selfCount = try {
                    W.nativeDiagnosticRichPresenceKeyCount(selfId)
                } catch (_: UnsatisfiedLinkError) { -1 }
                val aliceId = 76561197960287930L
                W.setFriendRichPresence(aliceId, "status", "Boss Battle")
                W.setFriendRichPresence(aliceId, "connect", "+lobby:7")
                val aliceCount = try {
                    W.nativeDiagnosticRichPresenceKeyCount(aliceId)
                } catch (_: UnsatisfiedLinkError) { -1 }
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "seedTestRichPresence: cb depth pre=$depth0 post=$depth1 " +
                    "(expect post-pre=4: 1 self-Set*2 + 2 friend-pushes), " +
                    "setOk=$setOk setOk2=$setOk2 selfKeyCount=$selfCount " +
                    "aliceKeyCount=$aliceCount (expect selfKeyCount=2 aliceKeyCount=2)")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestRichPresence threw", t)
            }
        } else if (op == "seedtestavatartiers") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val F = com.winlator.cmod.feature.stores.steam.wnsteam.AvatarFetcher
                val aliceId = 76561197960287931L
                val testHash = "fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb"
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                F.enqueueAllTiers(aliceId, testHash)
                var h0 = 0; var w0 = 0; var t0 = 0
                var h1 = 0; var w1 = 0; var t1 = 0
                var h2 = 0; var w2 = 0; var t2 = 0
                repeat(100) {
                    val p0 = try { W.nativeDiagnosticGetTieredAvatarSize(aliceId, 0) } catch (_: UnsatisfiedLinkError) { 0L }
                    val p1 = try { W.nativeDiagnosticGetTieredAvatarSize(aliceId, 1) } catch (_: UnsatisfiedLinkError) { 0L }
                    val p2 = try { W.nativeDiagnosticGetTieredAvatarSize(aliceId, 2) } catch (_: UnsatisfiedLinkError) { 0L }
                    h0 = (p0 shr 32).toInt(); w0 = ((p0 shr 16) and 0xFFFF).toInt(); t0 = (p0 and 0xFFFF).toInt()
                    h1 = (p1 shr 32).toInt(); w1 = ((p1 shr 16) and 0xFFFF).toInt(); t1 = (p1 and 0xFFFF).toInt()
                    h2 = (p2 shr 32).toInt(); w2 = ((p2 shr 16) and 0xFFFF).toInt(); t2 = (p2 and 0xFFFF).toInt()
                    if (h0 > 0 && h1 > 0 && h2 > 0) return@repeat
                    Thread.sleep(100)
                }
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "seedTestAvatarTiers: " +
                    "small(handle=$h0 size=${w0}x${t0}) " +
                    "medium(handle=$h1 size=${w1}x${t1}) " +
                    "large(handle=$h2 size=${w2}x${t2}) " +
                    "cb depth pre=$depth0 post=$depth1 " +
                    "(expect small=32x32 medium=64x64 large=184x184, post-pre=3)")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestAvatarTiers threw", t)
            }
        } else if (op == "seedtestschemacache") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val testAppId = 99999  // unlikely to collide with a real app
                val achievements = listOf(
                    com.winlator.cmod.feature.stores.steam.statsgen.Achievement(
                        name        = "ACH_CACHE_TEST_A",
                        displayName = mapOf("english" to "Cached A"),
                        description = mapOf("english" to "First cached achievement"),
                        hidden      = 0,
                        unlocked    = true,
                        unlockTimestamp = 1700000000,
                    ),
                    com.winlator.cmod.feature.stores.steam.statsgen.Achievement(
                        name        = "ACH_CACHE_TEST_B",
                        displayName = mapOf("english" to "Cached B"),
                        description = mapOf("english" to "Second cached achievement"),
                        hidden      = 1,
                    ),
                )
                val stats = listOf(
                    com.winlator.cmod.feature.stores.steam.statsgen.Stat(
                        id = "kills", name = "kills", type = "int", default = "0"),
                )
                SteamService.cacheAchievementSchemaJson(testAppId, achievements, stats)
                val cacheFile = java.io.File(
                    context.applicationContext.filesDir,
                    "wn_lsteam_schemas/$testAppId.json",
                )
                val fileLen = if (cacheFile.exists()) cacheFile.length() else -1L
                try {
                    W.setAchievementSchema(emptyArray(), emptyArray(), emptyArray(),
                                            emptyArray(), BooleanArray(0))
                } catch (_: UnsatisfiedLinkError) {}
                val countBefore = try { W.nativeDiagnosticAchievementCount() }
                                  catch (_: UnsatisfiedLinkError) { -1 }
                val warmed = SteamService.warmAchievementSchemaFromCache(testAppId)
                val countAfter = try { W.nativeDiagnosticAchievementCount() }
                                 catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "seedTestSchemaCache: testAppId=$testAppId cacheFileLen=$fileLen " +
                    "warmed=$warmed achCount before=$countBefore after=$countAfter " +
                    "(expect fileLen>0 warmed=true before=0 after=2)")
                try { cacheFile.delete() } catch (_: Exception) {}
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestSchemaCache threw", t)
            }
        } else if (op == "seedtestfriendsnapshot") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val P = com.winlator.cmod.feature.stores.steam.utils.PrefManager
                val prevSnap = P.friendsSnapshotJson
                try {
                    val json = """[
                        {"sid":76561197960287930,"name":"Alice Snapshot","state":1,"app":440,
                         "avatarHash":"fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb"},
                        {"sid":76561197960265728,"name":"Bob Snapshot","state":0,"app":0,
                         "avatarHash":""}
                    ]"""
                    val pushed = W.pushFriendPersonasJson(json, persistSnapshot = true)
                    val persistedLen = P.friendsSnapshotJson.length
                    val aliceState = try {
                        W.nativeDiagnosticGetFriendPersonaState(76561197960287930L)
                    } catch (_: UnsatisfiedLinkError) { -2 }
                    val bobState = try {
                        W.nativeDiagnosticGetFriendPersonaState(76561197960265728L)
                    } catch (_: UnsatisfiedLinkError) { -2 }
                    val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                    val replayed = W.pushFriendPersonasJson(P.friendsSnapshotJson, persistSnapshot = false)
                    val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                    Log.i(TAG, "seedTestFriendSnapshot: pushed=$pushed " +
                        "persistedJsonLen=$persistedLen aliceState=$aliceState bobState=$bobState " +
                        "replayed=$replayed cb depth pre=$depth0 post=$depth1 " +
                        "(expect pushed=2 persistedJsonLen>0 aliceState=1 bobState=0 " +
                        "replayed=2 — replay reuses same map, dedup-aware setters)")
                } finally {
                    P.friendsSnapshotJson = prevSnap
                }
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestFriendSnapshot threw", t)
            }
        } else if (op == "seedtestprefswarmup") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val P = com.winlator.cmod.feature.stores.steam.utils.PrefManager
                val prevSid    = P.steamUserSteamId64
                val prevName   = P.steamUserName
                val prevHash   = P.steamUserAvatarHash
                try {
                    val testSid  = 0x110000100DEADBE0L
                    val testName = "WarmupTester"
                    val testHash = "fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb"
                    P.steamUserSteamId64   = testSid
                    P.steamUserName        = testName
                    P.steamUserAvatarHash  = testHash
                    val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                    W.seedFromPrefManager(context.applicationContext)
                    var handle = 0
                    var w = 0
                    var h = 0
                    repeat(60) {
                        val packed = try {
                            W.nativeDiagnosticGetSmallAvatarSize(testSid)
                        } catch (_: UnsatisfiedLinkError) { 0L }
                        handle = (packed shr 32).toInt()
                        if (handle > 0) {
                            w = ((packed shr 16) and 0xFFFF).toInt()
                            h = (packed and 0xFFFF).toInt()
                            return@repeat
                        }
                        Thread.sleep(100)
                    }
                    val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                    val storedHashHex = try {
                        W.nativeDiagnosticGetFriendAvatarHashHex(testSid)
                    } catch (_: UnsatisfiedLinkError) { "?" }
                    Log.i(TAG, "seedTestPrefsWarmup: sid=$testSid handle=$handle " +
                        "size=${w}x${h} storedHash='$storedHashHex' " +
                        "cb depth pre=$depth0 post=$depth1 " +
                        "(expect handle>0 size=32x32 storedHash=$testHash " +
                        "post-pre≥2 — 1 PersonaStateChange + 1 AvatarImageLoaded)")
                } finally {
                    P.steamUserSteamId64  = prevSid
                    P.steamUserName       = prevName
                    P.steamUserAvatarHash = prevHash
                }
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestPrefsWarmup threw", t)
            }
        } else if (op == "seedtestselfavatar") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val F = com.winlator.cmod.feature.stores.steam.wnsteam.AvatarFetcher
                val selfId = 0x110000100C123456L
                W.setSteamId(selfId)
                val testHash = "fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb"
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val bytes = ByteArray(20)
                for (k in bytes.indices) {
                    val hi = Character.digit(testHash[k * 2], 16)
                    val lo = Character.digit(testHash[k * 2 + 1], 16)
                    bytes[k] = ((hi shl 4) or lo).toByte()
                }
                W.setFriendAvatarHash(selfId, bytes)
                F.enqueue(selfId, testHash, tier = 0)
                var handle = 0
                var w = 0
                var h = 0
                repeat(60) {
                    val packed = try {
                        W.nativeDiagnosticGetSmallAvatarSize(selfId)
                    } catch (_: UnsatisfiedLinkError) { 0L }
                    handle = (packed shr 32).toInt()
                    if (handle > 0) {
                        w = ((packed shr 16) and 0xFFFF).toInt()
                        h = (packed and 0xFFFF).toInt()
                        return@repeat
                    }
                    Thread.sleep(100)
                }
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "seedTestSelfAvatar: selfId=$selfId handle=$handle " +
                    "size=${w}x${h} cb depth pre=$depth0 post=$depth1 " +
                    "(expect handle>0, size=32x32, post-pre=2 — 1 PersonaStateChange + " +
                    "1 AvatarImageLoaded — proves self uses same friend_avatars map)")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestSelfAvatar threw", t)
            }
        } else if (op == "seedtestfetchavatar") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val F = com.winlator.cmod.feature.stores.steam.wnsteam.AvatarFetcher
                val aliceId = 76561197960287930L
                val testHash = "fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb"
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                F.enqueue(aliceId, testHash, tier = 0)
                var handle = 0
                var w = 0
                var h = 0
                repeat(60) {
                    val packed = try {
                        W.nativeDiagnosticGetSmallAvatarSize(aliceId)
                    } catch (_: UnsatisfiedLinkError) { 0L }
                    handle = (packed shr 32).toInt()
                    if (handle > 0) {
                        w = ((packed shr 16) and 0xFFFF).toInt()
                        h = (packed and 0xFFFF).toInt()
                        return@repeat
                    }
                    Thread.sleep(100)
                }
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "seedTestFetchAvatar: hash=$testHash handle=$handle " +
                    "size=${w}x${h} cb depth pre=$depth0 post=$depth1 " +
                    "(expect handle>0, size=32x32 or similar, " +
                    "post-pre=1 AvatarImageLoaded — assumes network reachable)")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestFetchAvatar threw", t)
            }
        } else if (op == "seedtestavatarhash") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val aliceId = 76561197960287930L
                val hashA = ByteArray(20) { (it * 13 + 7).toByte() }
                val hashB = ByteArray(20) { (it * 17 + 3).toByte() }
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.setFriendAvatarHash(aliceId, hashA)        // change → +1
                W.setFriendAvatarHash(aliceId, hashA)        // dedup → +0
                val depthMid = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.setFriendAvatarHash(aliceId, hashB)        // change → +1
                W.setFriendAvatarHash(aliceId, null)         // clear → +1
                W.setFriendAvatarHash(aliceId, null)         // dedup → +0
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val finalHex = try {
                    W.nativeDiagnosticGetFriendAvatarHashHex(aliceId)
                } catch (_: UnsatisfiedLinkError) { "?" }
                Log.i(TAG, "seedTestAvatarHash: cb depth pre=$depth0 mid=$depthMid post=$depth1 " +
                    "finalHex='$finalHex' " +
                    "(expect mid-pre=1 post-mid=2 finalHex='' — 3 transitions, 2 dedups)")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestAvatarHash threw", t)
            }
        } else if (op == "seedtestavatar") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val aliceId = 76561197960287930L
                val w = 4
                val h = 4
                val rgba = ByteArray(w * h * 4)
                for (y in 0 until h) for (x in 0 until w) {
                    val i = (y * w + x) * 4
                    rgba[i + 0] = (x * 64).toByte()
                    rgba[i + 1] = (y * 64).toByte()
                    rgba[i + 2] = 0xAA.toByte()
                    rgba[i + 3] = 0xFF.toByte()
                }
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val handle = try {
                    W.nativePushFriendAvatar(aliceId, tier = 0, width = w, height = h, rgba = rgba)
                } catch (_: UnsatisfiedLinkError) { 0 }
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val packed = try {
                    W.nativeDiagnosticGetSmallAvatarSize(aliceId)
                } catch (_: UnsatisfiedLinkError) { 0L }
                val rdHandle = (packed shr 32).toInt()
                val rdW = ((packed shr 16) and 0xFFFF).toInt()
                val rdH = (packed and 0xFFFF).toInt()
                val readBuf = ByteArray(w * h * 4)
                val copied = try {
                    W.nativeDiagnosticGetImageRGBA(handle, readBuf)
                } catch (_: UnsatisfiedLinkError) { 0 }
                val px2_1 = (2 + 1 * w) * 4
                val r = readBuf[px2_1 + 0].toInt() and 0xFF
                val g = readBuf[px2_1 + 1].toInt() and 0xFF
                val b = readBuf[px2_1 + 2].toInt() and 0xFF
                val a = readBuf[px2_1 + 3].toInt() and 0xFF
                Log.i(TAG, "seedTestAvatar: handle=$handle (cb depth pre=$depth0 post=$depth1) " +
                    "rdHandle=$rdHandle rdW=$rdW rdH=$rdH copied=$copied " +
                    "px(2,1) RGBA=($r,$g,$b,$a) " +
                    "(expect post-pre=1 AvatarImageLoaded, rdW=4 rdH=4, RGBA=(128,64,170,255))")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestAvatar threw", t)
            }
        } else if (op == "probebridgepersonaself") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val selfSid = 0x110000100C123498L
                W.setSteamId(selfSid)
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                try {
                    W.nativeDiagnosticInjectPersonaEvent(
                        steamId      = selfSid,         // matches setSteamId → observer takes self path
                        personaState = 3,                // Away
                        gameAppId    = 730,
                        name         = "SelfPathProbe",
                        avatarHash   = null,
                        rpKeys       = null,
                        rpValues     = null,
                    )
                } catch (_: UnsatisfiedLinkError) {}
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val friendStateAtSelfSid = try {
                    W.nativeDiagnosticGetFriendPersonaState(selfSid)
                } catch (_: UnsatisfiedLinkError) { -2 }
                Log.i(TAG, "probeBridgePersonaSelf: cb depth pre=$depth0 post=$depth1 " +
                    "friendStateAtSelfSid=$friendStateAtSelfSid " +
                    "(expect cb Δ=+1 self PSC; friendStateAtSelfSid=-1 " +
                    "[self path writes pushed.persona_state, NOT the friend map])")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgePersonaSelf threw", t)
            }
        } else if (op == "probebridgepersona") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val sid = 76561197960287936L
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val hash = ByteArray(20) { (it * 11 + 5).toByte() }
                try {
                    W.nativeDiagnosticInjectPersonaEvent(
                        steamId      = sid,
                        personaState = 1,
                        gameAppId    = 440,
                        name         = "PersonaProbe",
                        avatarHash   = hash,
                        rpKeys       = arrayOf("status", "connect"),
                        rpValues     = arrayOf("In Lobby", "+lobby:42"),
                    )
                } catch (_: UnsatisfiedLinkError) {}
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val st = try { W.nativeDiagnosticGetFriendPersonaState(sid) } catch (_: UnsatisfiedLinkError) { -2 }
                val hashHex = try { W.nativeDiagnosticGetFriendAvatarHashHex(sid) } catch (_: UnsatisfiedLinkError) { "?" }
                val rpCount = try { W.nativeDiagnosticRichPresenceKeyCount(sid) } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "probeBridgePersona: cb depth pre=$depth0 post=$depth1 " +
                    "state=$st hashLen=${hashHex.length / 2} rpCount=$rpCount " +
                    "(expect cb Δ=+2 PSC[name|state|game|avatar|come-online] + " +
                    "FriendRichPresenceUpdate_t; state=1, hashLen=20, rpCount=2)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgePersona threw", t)
            }
        } else if (op == "probenicksig") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val sid = 0x0110000100006666L
                val nickBefore = W.nativeDiagnosticGetPlayerNickname(sid)
                W.nativeSetPlayerNickname(sid, "Sniper42")
                val nickAfter = W.nativeDiagnosticGetPlayerNickname(sid)
                W.nativeSetPlayerNickname(sid, null)
                val nickCleared = W.nativeDiagnosticGetPlayerNickname(sid)
                val hSig1 = W.nativeDiagnosticCheckFileSignature("bin/game.exe")
                val hSig2 = W.nativeDiagnosticCheckFileSignature(null)
                Log.i(TAG, "probeNickSig: nickBefore=$nickBefore (null), " +
                    "nickAfter=$nickAfter (Sniper42), nickCleared=$nickCleared (null), " +
                    "hSig1=$hSig1 hSig2=$hSig2 (both non-zero+distinct: " +
                    "${hSig1 != 0L && hSig2 != 0L && hSig1 != hSig2})")
            } catch (t: Throwable) {
                Log.e(TAG, "probeNickSig threw", t)
            }
        } else if (op == "probecloudshare") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 9900
                val dir = java.io.File(context.cacheDir, "probe-cloud-share-$appId")
                dir.mkdirs()
                W.nativeSetAppCloudRemoteDir(appId, dir.absolutePath)
                W.setAppId(appId)
                W.nativeDiagnosticCloudFileWrite("share.dat", "shareable".toByteArray())
                val hShare1 = W.nativeDiagnosticCloudFileShare("share.dat")
                val hShare2 = W.nativeDiagnosticCloudFileShare("share.dat")
                val hShareMiss = W.nativeDiagnosticCloudFileShare("missing.dat")
                W.setAppInstallDir(appId, dir.absolutePath)
                java.io.File(dir, "binary.dat").writeBytes("xyz123".toByteArray())
                val hDetails = W.nativeDiagnosticAppsGetFileDetails("binary.dat")
                val hDetailsMiss = W.nativeDiagnosticAppsGetFileDetails("nope.dat")
                val hDetailsTraversal = W.nativeDiagnosticAppsGetFileDetails("../escape")
                W.nativeDiagnosticCloudFileDelete("share.dat")
                W.setAppId(0)
                W.nativeSetAppCloudRemoteDir(appId, null)
                Log.i(TAG, "probeCloudShare: " +
                    "share1=$hShare1 share2=$hShare2 (distinct hCalls), " +
                    "shareMissing=$hShareMiss, " +
                    "details=$hDetails detailsMissing=$hDetailsMiss " +
                    "detailsTraversal=$hDetailsTraversal " +
                    "(all non-zero: ${hShare1 != 0L && hShare2 != 0L && hShareMiss != 0L && hDetails != 0L && hDetailsMiss != 0L && hDetailsTraversal != 0L})")
            } catch (t: Throwable) {
                Log.e(TAG, "probeCloudShare threw", t)
            }
        } else if (op == "probeplayerlevel") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 6500
                W.setAppId(appId)
                W.nativeSetSelfPlayerLevel(73)
                val plr = W.nativeDiagnosticGetPlayerSteamLevel()
                W.nativeSetSelfGameBadge(appId, 1, false, 5)
                W.nativeSetSelfGameBadge(appId, 1, true,  3)
                val badgeBasic = W.nativeDiagnosticGetGameBadgeLevel(1, false)
                val badgeFoil  = W.nativeDiagnosticGetGameBadgeLevel(1, true)
                val badgeOther = W.nativeDiagnosticGetGameBadgeLevel(2, false)
                val hStore   = W.nativeDiagnosticRequestStoreAuthURL("checkout/")
                val hMarket  = W.nativeDiagnosticGetMarketEligibility()
                val hDuration = W.nativeDiagnosticGetDurationControl()
                W.setAppId(0)
                W.nativeSetSelfPlayerLevel(-1)
                W.nativeSetSelfGameBadge(appId, 1, false, -1)
                W.nativeSetSelfGameBadge(appId, 1, true,  -1)
                Log.i(TAG, "probePlayerLevel: " +
                    "playerLevel=$plr (expect 73), " +
                    "badgeBasic=$badgeBasic (5), badgeFoil=$badgeFoil (3), " +
                    "badgeOther=$badgeOther (0), " +
                    "hStoreUrl=$hStore hMarket=$hMarket hDuration=$hDuration " +
                    "(all non-zero+distinct: ${hStore != 0L && hMarket != 0L && hDuration != 0L && hStore != hMarket && hMarket != hDuration})")
            } catch (t: Throwable) {
                Log.e(TAG, "probePlayerLevel threw", t)
            }
        } else if (op == "probelicense") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val selfSid = 0x0110000100009999L
                val otherSid = 0x0110000100008888L
                val ownedApp = 7700
                val unownedApp = 7800
                W.setSteamId(selfSid)
                W.setOwnedApps(intArrayOf(ownedApp))
                val r1 = W.nativeDiagnosticUserHasLicense(selfSid, ownedApp)
                val r2 = W.nativeDiagnosticUserHasLicense(selfSid, unownedApp)
                val r3 = W.nativeDiagnosticUserHasLicense(otherSid, ownedApp)
                val r4 = W.nativeDiagnosticUserHasLicense(0L, ownedApp)
                val r5 = W.nativeDiagnosticUserHasLicense(selfSid, 0)
                W.setOwnedApps(IntArray(0))
                W.setSteamId(0)
                Log.i(TAG, "probeLicense: self+owned=$r1 (expect 0/HasLicense), " +
                    "self+unowned=$r2 (expect 1/DoesNotHave), " +
                    "other+owned=$r3 (expect 2/NoAuth), " +
                    "nullSid+owned=$r4 (expect 2/NoAuth), " +
                    "self+nullApp=$r5 (expect 2/NoAuth)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeLicense threw", t)
            }
        } else if (op == "probecorrupt") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 7900
                W.setAppId(appId)
                val markedBefore = W.nativeIsAppMarkedCorrupt(appId)
                val markOk = W.nativeDiagnosticMarkContentCorrupt(false)
                val markedAfter = W.nativeIsAppMarkedCorrupt(appId)
                W.nativeClearAppCorruptFlag(appId)
                val markedCleared = W.nativeIsAppMarkedCorrupt(appId)
                W.setAppId(0)
                val markNoApp = W.nativeDiagnosticMarkContentCorrupt(true)
                Log.i(TAG, "probeCorrupt: before=$markedBefore (false), " +
                    "mark=$markOk (true), after=$markedAfter (true), " +
                    "cleared=$markedCleared (false), markNoApp=$markNoApp (false)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeCorrupt threw", t)
            }
        } else if (op == "probefriendlevel") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val sid = 0x0110000100007777L
                val before = W.nativeDiagnosticGetFriendSteamLevel(sid)
                W.nativeSetFriendSteamLevel(sid, 42)
                val after = W.nativeDiagnosticGetFriendSteamLevel(sid)
                W.nativeSetFriendSteamLevel(sid, -1)
                val cleared = W.nativeDiagnosticGetFriendSteamLevel(sid)
                Log.i(TAG, "probeFriendLevel: before=$before (0), " +
                    "after=$after (42), cleared=$cleared (0)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeFriendLevel threw", t)
            }
        } else if (op == "probewebapiticket") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val h1 = W.nativeDiagnosticGetAuthTicketForWebApi("myWebApi")
                val h2 = W.nativeDiagnosticGetAuthTicketForWebApi(null)
                val h3 = W.nativeDiagnosticGetAuthTicketForWebApi("anotherDest")
                Log.i(TAG, "probeWebApiTicket: h1=$h1 h2=$h2 h3=$h3 " +
                    "(all non-zero, distinct: ${h1 != 0L && h2 != 0L && h3 != 0L && h1 != h2 && h2 != h3})")
            } catch (t: Throwable) {
                Log.e(TAG, "probeWebApiTicket threw", t)
            }
        } else if (op == "probefriendrel") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val friend1 = 0x0110000100000001L
                val friend2 = 0x0110000100000002L
                val stranger = 0x0110000100000099L
                W.setFriendsList(longArrayOf(friend1, friend2))
                val rel1   = W.nativeDiagnosticGetFriendRelationship(friend1)
                val relStr = W.nativeDiagnosticGetFriendRelationship(stranger)
                val rel0   = W.nativeDiagnosticGetFriendRelationship(0L)
                val hasImmediate = W.nativeDiagnosticHasFriend(friend2, 0x10)
                val hasAll       = W.nativeDiagnosticHasFriend(friend2, 0xFFFF)
                val hasClanOnly  = W.nativeDiagnosticHasFriend(friend2, 0x20)
                val hasStranger  = W.nativeDiagnosticHasFriend(stranger, 0xFFFF)
                W.setFriendsList(LongArray(0))
                Log.i(TAG, "probeFriendRel: friend=$rel1 (expect 3), " +
                    "stranger=$relStr (expect 0), null=$rel0 (expect 0), " +
                    "hasImmediate=$hasImmediate (true), hasAll=$hasAll (true), " +
                    "hasClanOnly=$hasClanOnly (false), hasStranger=$hasStranger (false)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeFriendRel threw", t)
            }
        } else if (op == "probeuserdata") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 9800
                val remoteDir = "/data/cache/.../userdata/123/$appId/remote"
                W.nativeSetAppCloudRemoteDir(appId, remoteDir)
                W.setAppId(appId)
                val resolved = W.nativeDiagnosticGetUserDataFolder()
                W.setAppId(0)
                W.nativeSetAppCloudRemoteDir(appId, null)
                val expected = "/data/cache/.../userdata/123/$appId"
                Log.i(TAG, "probeUserData: resolved=\"$resolved\" expected=\"$expected\" " +
                    "match=${resolved == expected}")
            } catch (t: Throwable) {
                Log.e(TAG, "probeUserData threw", t)
            }
        } else if (op == "probeaccountinfo") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                W.nativeDiagnosticInjectAccountInfo(false, false, false, false)
                val cleared26 = W.nativeDiagnosticUserBool(26)
                val cleared27 = W.nativeDiagnosticUserBool(27)
                W.nativeDiagnosticInjectAccountInfo(true, false, true, false)
                val v26 = W.nativeDiagnosticUserBool(26)  // phone-verified=false
                val v27 = W.nativeDiagnosticUserBool(27)  // 2FA=true
                val v28 = W.nativeDiagnosticUserBool(28)  // phone-identifying=true
                val v29 = W.nativeDiagnosticUserBool(29)  // phone-needs-verify=false
                Log.i(TAG, "probeAccountInfo: cleared(26=$cleared26, 27=$cleared27) " +
                    "after-inject(26=$v26 expect false, 27=$v27 expect true, " +
                    "28=$v28 expect true, 29=$v29 expect false)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeAccountInfo threw", t)
            }
        } else if (op == "probeuserbool") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                for (k in 0..3) W.nativeSetAccountFlag(k, true)
                val v26 = W.nativeDiagnosticUserBool(26)  // phone-verified
                val v27 = W.nativeDiagnosticUserBool(27)  // 2FA
                val v28 = W.nativeDiagnosticUserBool(28)  // phone-identifying
                val v29 = W.nativeDiagnosticUserBool(29)  // phone-needs-verify
                for (k in 0..3) W.nativeSetAccountFlag(k, false)
                val o26 = W.nativeDiagnosticUserBool(26)
                val o27 = W.nativeDiagnosticUserBool(27)
                val o28 = W.nativeDiagnosticUserBool(28)
                val o29 = W.nativeDiagnosticUserBool(29)
                val dur = W.nativeDiagnosticSetDurationControl(1)
                Log.i(TAG, "probeUserBool: " +
                    "set→phoneV=$v26 2FA=$v27 phoneIdent=$v28 phoneNeedsV=$v29 (all true); " +
                    "cleared→phoneV=$o26 2FA=$o27 phoneIdent=$o28 phoneNeedsV=$o29 (all false); " +
                    "durationControl=$dur (always true)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeUserBool threw", t)
            }
        } else if (op == "probeappsbool") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 9600
                W.setOwnedApps(intArrayOf(appId))
                W.nativeSetAppFlag(0, appId, true)   // low-violence
                W.nativeSetAppFlag(1, appId, true)   // vac-banned
                W.setAppId(appId)
                val sub  = W.nativeDiagnosticAppsBool(0)
                val lowV = W.nativeDiagnosticAppsBool(1)
                val cyber = W.nativeDiagnosticAppsBool(2)
                val vac  = W.nativeDiagnosticAppsBool(3)
                W.setOwnedApps(IntArray(0))
                W.nativeSetAppFlag(0, appId, false)
                W.nativeSetAppFlag(1, appId, false)
                val subOff  = W.nativeDiagnosticAppsBool(0)
                val lowVOff = W.nativeDiagnosticAppsBool(1)
                val vacOff  = W.nativeDiagnosticAppsBool(3)
                val ctxOk = W.nativeDiagnosticSetDlcContext(appId)
                W.setAppId(0)
                Log.i(TAG, "probeAppsBool: " +
                    "subscribed=$sub (true), lowV=$lowV (true), " +
                    "cyber=$cyber (always false), vac=$vac (true); " +
                    "cleared → subOff=$subOff lowVOff=$lowVOff vacOff=$vacOff (all false), " +
                    "setDlcContext=$ctxOk (true)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeAppsBool threw", t)
            }
        } else if (op == "probefilestate") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 9700
                val dir = java.io.File(context.cacheDir, "probe-filestate-$appId")
                dir.mkdirs()
                W.nativeSetAppCloudRemoteDir(appId, dir.absolutePath)
                W.setAppId(appId)
                W.nativeDiagnosticCloudFileWrite("note.dat", "x".toByteArray())
                val persistedBefore = W.nativeDiagnosticCloudFilePersisted("note.dat")
                val forgotten = W.nativeDiagnosticCloudFileForget("note.dat")
                val persistedAfter = W.nativeDiagnosticCloudFilePersisted("note.dat")
                val forgottenAgain = W.nativeDiagnosticCloudFileForget("note.dat")
                val onDisk = java.io.File(dir, "note.dat").exists()
                W.nativeDiagnosticCloudFileDelete("note.dat")
                W.setAppId(0)
                W.nativeSetAppCloudRemoteDir(appId, null)
                Log.i(TAG, "probeFileState: persistedBefore=$persistedBefore (true), " +
                    "forgotten=$forgotten (true), persistedAfter=$persistedAfter (false), " +
                    "forgottenAgain=$forgottenAgain (false), diskKept=$onDisk (true)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeFileState threw", t)
            }
        } else if (op == "probecloudstream") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 9500
                val dir = java.io.File(context.cacheDir, "probe-cloud-stream-$appId")
                dir.mkdirs()
                W.nativeSetAppCloudRemoteDir(appId, dir.absolutePath)
                W.setAppId(appId)
                val h1 = W.nativeDiagnosticCloudStreamOpen("stream.dat")
                val c1 = W.nativeDiagnosticCloudStreamWriteChunk(h1, "AAA".toByteArray())
                val c2 = W.nativeDiagnosticCloudStreamWriteChunk(h1, "BBBB".toByteArray())
                val c3 = W.nativeDiagnosticCloudStreamWriteChunk(h1, "CC".toByteArray())
                val closed = W.nativeDiagnosticCloudStreamClose(h1)
                val readBack = W.nativeDiagnosticCloudFileRead("stream.dat", 64)
                val match = readBack != null && readBack.contentEquals("AAABBBBCC".toByteArray())
                val h2 = W.nativeDiagnosticCloudStreamOpen("cancelled.dat")
                W.nativeDiagnosticCloudStreamWriteChunk(h2, "ZZZ".toByteArray())
                val cancelled = W.nativeDiagnosticCloudStreamCancel(h2)
                val missing = W.nativeDiagnosticCloudFileRead("cancelled.dat", 16)
                val bogusClose  = W.nativeDiagnosticCloudStreamClose(0x999999L)
                val bogusCancel = W.nativeDiagnosticCloudStreamCancel(0x999999L)
                W.nativeDiagnosticCloudFileDelete("stream.dat")
                W.setAppId(0)
                W.nativeSetAppCloudRemoteDir(appId, null)
                Log.i(TAG, "probeCloudStream: open1=$h1 c1=$c1 c2=$c2 c3=$c3 " +
                    "close=$closed match=$match (expect AAABBBBCC), " +
                    "cancel=$cancelled miss-after-cancel=${missing == null}, " +
                    "bogusClose=$bogusClose bogusCancel=$bogusCancel (both false)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeCloudStream threw", t)
            }
        } else if (op == "probecloudasync") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 9400
                val dir = java.io.File(context.cacheDir, "probe-cloud-async-$appId")
                dir.mkdirs()
                W.nativeSetAppCloudRemoteDir(appId, dir.absolutePath)
                W.setAppId(appId)
                val payload = "async-roundtrip-${System.currentTimeMillis()}".toByteArray()
                val wH = W.nativeDiagnosticCloudFileWriteAsync("async.dat", payload)
                val rH = W.nativeDiagnosticCloudFileReadAsync("async.dat", 0, payload.size)
                val bytes = W.nativeDiagnosticCloudFileReadAsyncComplete(rH, payload.size)
                val match = bytes != null && bytes.contentEquals(payload)
                val bytes2 = W.nativeDiagnosticCloudFileReadAsyncComplete(rH, payload.size)
                W.nativeDiagnosticCloudFileDelete("async.dat")
                W.setAppId(0)
                W.nativeSetAppCloudRemoteDir(appId, null)
                Log.i(TAG, "probeCloudAsync: writeAsync-hCall=$wH (expect non-zero), " +
                    "readAsync-hCall=$rH (expect non-zero), " +
                    "roundtrip-match=$match (expect true), " +
                    "second-complete-null=${bytes2 == null} (expect true)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeCloudAsync threw", t)
            }
        } else if (op == "probecloudio") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 9300
                val dir = java.io.File(context.cacheDir, "probe-cloud-$appId")
                dir.mkdirs()
                W.nativeSetAppCloudRemoteDir(appId, dir.absolutePath)
                W.setAppId(appId)
                val payload = "hello-steam-cloud-${System.currentTimeMillis()}".toByteArray()
                val wOk  = W.nativeDiagnosticCloudFileWrite("save.dat", payload)
                val read = W.nativeDiagnosticCloudFileRead("save.dat", 256)
                val match = read != null && read.contentEquals(payload)
                val rejectW = W.nativeDiagnosticCloudFileWrite("../escape", byteArrayOf(1))
                val rejectR = W.nativeDiagnosticCloudFileRead("../escape", 8)
                val dOk = W.nativeDiagnosticCloudFileDelete("save.dat")
                val readAfter = W.nativeDiagnosticCloudFileRead("save.dat", 256)
                W.setAppId(0)
                W.nativeSetAppCloudRemoteDir(appId, null)
                Log.i(TAG, "probeCloudIO: write=$wOk (expect true), " +
                    "read-roundtrip=$match (expect true), " +
                    "rejectTraversalW=$rejectW (expect false), " +
                    "rejectTraversalR=${rejectR != null} (expect false), " +
                    "delete=$dOk (expect true), " +
                    "read-after-delete=${readAfter != null} (expect false)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeCloudIO threw", t)
            }
        } else if (op == "probedlprogress") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 9200
                W.nativeSetAppDownloadProgress(appId, 1234L, 5000L)
                val active = W.nativeDiagnosticGetDlcDownloadProgress(appId)
                val dl = W.nativeDiagnosticGetDlcDownloadProgressBytes()
                val tot = W.nativeDiagnosticGetDlcDownloadProgressTotal()
                W.nativeSetAppDownloadProgress(appId, 0L, 0L)
                val cleared = W.nativeDiagnosticGetDlcDownloadProgress(appId)
                val unknown = W.nativeDiagnosticGetDlcDownloadProgress(99999)
                Log.i(TAG, "probeDlProgress: active=$active (expect true) " +
                    "dl=$dl (expect 1234) total=$tot (expect 5000), " +
                    "cleared=$cleared (expect false), " +
                    "unknown-app=$unknown (expect false)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeDlProgress threw", t)
            }
        } else if (op == "probebetaname") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val appId = 9100
                W.nativeSetAppCurrentBeta(appId, "experimental_branch")
                W.setAppId(appId)
                val name = W.nativeDiagnosticGetCurrentBetaName()
                W.nativeSetAppCurrentBeta(appId, null)
                val cleared = W.nativeDiagnosticGetCurrentBetaName()
                W.setAppId(0)
                Log.i(TAG, "probeBetaName: bound→\"$name\" (expect experimental_branch), " +
                    "cleared→\"$cleared\" (expect null)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBetaName threw", t)
            }
        } else if (op == "probedlcinstalled") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val parent = 8100
                val dlc1 = 8101; val dlc2 = 8102; val dlc3 = 8103; val dlc4 = 8104
                W.setOwnedApps(intArrayOf(parent, dlc1, dlc2, dlc3))
                W.setInstalledApps(intArrayOf(parent, dlc1))
                W.setAppDlcs(
                    parent,
                    intArrayOf(dlc1, dlc2, dlc3, dlc4),
                    arrayOf("dlc-one", "dlc-two", "dlc-three", "dlc-four"),
                    booleanArrayOf(true, true, true, true))
                val r1 = W.nativeDiagnosticBIsDlcInstalled(dlc1)
                val r2 = W.nativeDiagnosticBIsDlcInstalled(dlc2)
                val r3 = W.nativeDiagnosticBIsDlcInstalled(dlc3)
                val r4 = W.nativeDiagnosticBIsDlcInstalled(dlc4)
                Log.i(TAG, "probeDlcInstalled: " +
                    "dlc1 owned+self-installed=$r1 (expect true), " +
                    "dlc2 owned+parent-installed=$r2 (expect true), " +
                    "dlc3 owned+parent-installed=$r3 (expect true, parent in set), " +
                    "dlc4 unowned=$r4 (expect false)")
                W.setInstalledApps(intArrayOf(dlc1))
                val r3b = W.nativeDiagnosticBIsDlcInstalled(dlc3)
                Log.i(TAG, "probeDlcInstalled: dlc3 with parent removed=$r3b (expect false)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeDlcInstalled threw", t)
            }
        } else if (op == "probetimedtrial") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                W.nativeDiagnosticInjectTrialLicense(5001, 90, 12)
                W.nativeSetAppSourcePackages(5500, intArrayOf(5001))
                W.setAppId(5500)
                val packed = try { W.nativeDiagnosticBIsTimedTrial() }
                             catch (_: UnsatisfiedLinkError) { 0L }
                val isTrial = packed != 0L
                val allowed = ((packed shr 32) and 0x7FFFFFFFL).toInt()
                val played  = (packed and 0xFFFFFFFFL).toInt()
                W.setAppId(0)
                W.nativeDiagnosticInjectTrialLicense(5002, 0, 0)
                W.nativeSetAppSourcePackages(5600, intArrayOf(5002))
                W.setAppId(5600)
                val nonTrialPacked = try { W.nativeDiagnosticBIsTimedTrial() }
                                     catch (_: UnsatisfiedLinkError) { 0L }
                val isNonTrial = nonTrialPacked != 0L
                W.setAppId(0)
                Log.i(TAG, "probeTimedTrial: trial app: isTrial=$isTrial " +
                    "allowed=$allowed seconds (expect 5400, =90*60), " +
                    "played=$played seconds (expect 720, =12*60); " +
                    "non-trial app: isTrial=$isNonTrial (expect false)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeTimedTrial threw", t)
            }
        } else if (op == "probeavgrate") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val r1 = try { W.nativeDiagnosticUpdateAvgRateStat("kph_test", 30.0f, 60.0) }
                         catch (_: UnsatisfiedLinkError) { 0.0f }
                val r2 = try { W.nativeDiagnosticUpdateAvgRateStat("kph_test", 60.0f, 60.0) }
                         catch (_: UnsatisfiedLinkError) { 0.0f }
                val r3 = try { W.nativeDiagnosticUpdateAvgRateStat("kph_test", 30.0f, 60.0) }
                         catch (_: UnsatisfiedLinkError) { 0.0f }
                Log.i(TAG, "probeAvgRate: session1(30/60)=$r1 (expect 0.5), " +
                    "session2(+60/+60)=$r2 (expect ~0.75), " +
                    "session3(+30/+60)=$r3 (expect ~0.6667)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeAvgRate threw", t)
            }
        } else if (op == "probeappowner") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val selfSid = 0x0110000100000000L or 0xCAFE1234L
                W.setSteamId(selfSid)
                val selfAcct  = 0xCAFE1234.toInt()
                val otherAcct = 0x11111111.toInt()
                W.nativeDiagnosticInjectLicenseList(
                    packageIds = intArrayOf(4001, 4002),
                    ownerIds   = intArrayOf(selfAcct, otherAcct))
                W.nativeSetAppSourcePackages(1100, intArrayOf(4001))
                W.nativeSetAppSourcePackages(2200, intArrayOf(4002))
                W.setOwnedApps(intArrayOf(1100, 2200))

                W.setAppId(1100)
                val ownerDirect = try { W.nativeDiagnosticGetAppOwner() }
                                  catch (_: UnsatisfiedLinkError) { 0L }
                W.setAppId(2200)
                val ownerShared = try { W.nativeDiagnosticGetAppOwner() }
                                  catch (_: UnsatisfiedLinkError) { 0L }
                W.setAppId(0)

                val expectedShared = 0x0110000100000000L or 0x11111111L
                Log.i(TAG, "probeAppOwner: " +
                    "direct(1100)=${java.lang.Long.toHexString(ownerDirect)} " +
                    "shared(2200)=${java.lang.Long.toHexString(ownerShared)} " +
                    "(expect direct=${java.lang.Long.toHexString(selfSid)}, " +
                    "shared=${java.lang.Long.toHexString(expectedShared)})")
            } catch (t: Throwable) {
                Log.e(TAG, "probeAppOwner threw", t)
            }
        } else if (op == "probefamilyshare") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val selfSid = 0x0110000100000000L or 0xCAFE1234L
                W.setSteamId(selfSid)
                val selfAcct = 0xCAFE1234.toInt()  // ~ -889004492 as signed
                W.nativeDiagnosticInjectLicenseList(
                    packageIds = intArrayOf(3001, 3002),
                    ownerIds   = intArrayOf(selfAcct, 0x11111111))
                W.nativeSetAppSourcePackages(1000, intArrayOf(3001))
                W.nativeSetAppSourcePackages(2000, intArrayOf(3002))
                W.nativeSetAppSourcePackages(3000, intArrayOf(3001, 3002))
                W.setAppId(1000)
                val r1 = try { W.nativeDiagnosticBIsSubscribedFromFamilySharing() }
                         catch (_: UnsatisfiedLinkError) { false }
                W.setAppId(2000)
                val r2 = try { W.nativeDiagnosticBIsSubscribedFromFamilySharing() }
                         catch (_: UnsatisfiedLinkError) { false }
                W.setAppId(3000)
                val r3 = try { W.nativeDiagnosticBIsSubscribedFromFamilySharing() }
                         catch (_: UnsatisfiedLinkError) { false }
                W.setAppId(0)  // reset
                Log.i(TAG, "probeFamilyShare: app1000_selfOnly=$r1 (expect false) " +
                    "app2000_otherOnly=$r2 (expect true) " +
                    "app3000_mixed=$r3 (expect false)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeFamilyShare threw", t)
            }
        } else if (op == "probepurchasetime") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val pkgs = intArrayOf(2001, 2002, 2003)
                val owners = intArrayOf(0, 0, 0)
                W.nativeDiagnosticInjectLicenseList(pkgs, owners)
                val testAppId = 9999
                W.nativeSetAppSourcePackages(testAppId, intArrayOf(2001, 2002))
                val earliest = try {
                    W.nativeDiagnosticGetEarliestPurchaseUnixTime(testAppId)
                } catch (_: UnsatisfiedLinkError) { -1 }
                val now = (System.currentTimeMillis() / 1000).toInt()
                val withinWindow = earliest > 0 && (now - earliest) < 60  // sanity: < 60s old
                val unknownApp = try {
                    W.nativeDiagnosticGetEarliestPurchaseUnixTime(99988)
                } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "probePurchaseTime: app=$testAppId earliest=$earliest " +
                    "now=$now withinWindow=$withinWindow " +
                    "unknownApp(99988)=$unknownApp " +
                    "(expect earliest within 60s of now; unknownApp=0)")
            } catch (t: Throwable) {
                Log.e(TAG, "probePurchaseTime threw", t)
            }
        } else if (op == "probebridgelicenses") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val packageIds = intArrayOf(10001, 10002, 10003)
                val ownerIds   = intArrayOf(0, 99999999, 77777777)
                try { W.nativeDiagnosticInjectLicenseList(packageIds, ownerIds) }
                catch (_: UnsatisfiedLinkError) {}
                val o1 = try { W.nativeDiagnosticGetLicenseOwner(10001) } catch (_: UnsatisfiedLinkError) { -2 }
                val o2 = try { W.nativeDiagnosticGetLicenseOwner(10002) } catch (_: UnsatisfiedLinkError) { -2 }
                val o3 = try { W.nativeDiagnosticGetLicenseOwner(10003) } catch (_: UnsatisfiedLinkError) { -2 }
                val miss = try { W.nativeDiagnosticGetLicenseOwner(99999) } catch (_: UnsatisfiedLinkError) { -2 }
                Log.i(TAG, "probeBridgeLicenses: injected 3 licenses; " +
                    "owner(10001)=$o1 (expect 0=self-owned), " +
                    "owner(10002)=$o2 (expect 99999999=family-share-A), " +
                    "owner(10003)=$o3 (expect 77777777=family-share-B), " +
                    "owner(99999)=$miss (expect -1=absent); " +
                    "look for 'license-list observer: 3 license(s) mirrored'")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgeLicenses threw", t)
            }
        } else if (op == "probebridgefriends") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val sids = longArrayOf(
                    76561197960287930L,
                    76561197960265728L,
                    76561197960123456L,
                )
                try { W.nativeDiagnosticInjectFriendsList(sids) }
                catch (_: UnsatisfiedLinkError) {}
                val bs = com.winlator.cmod.feature.stores.steam.wnsteam.WnSteamBootstrap
                val frCount = bs.friendCount()
                Log.i(TAG, "probeBridgeFriends: injected 3 SIDs; bootstrap-side " +
                    "friendCount()=$frCount (expect 3 when bootstrap alive — " +
                    "look for 'friends-list observer: 3 mutual friend(s) mirrored' " +
                    "log line for direct verification)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgeFriends threw", t)
            }
        } else if (op == "probebridgelogon") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                try { W.nativeDiagnosticInjectLogonState(false) } catch (_: UnsatisfiedLinkError) {}
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                try { W.nativeDiagnosticInjectLogonState(true) } catch (_: UnsatisfiedLinkError) {}
                val depth2 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                try { W.nativeDiagnosticInjectLogonState(true) } catch (_: UnsatisfiedLinkError) {}
                val depth3 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                try { W.nativeDiagnosticInjectLogonState(false) } catch (_: UnsatisfiedLinkError) {}
                val depth4 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "probeBridgeLogon: cb depth $depth0 → $depth1 → $depth2 → $depth3 → $depth4 " +
                    "(expect Δ pattern: ? 0 +1 0 +1; the first transition is " +
                    "implementation-dependent on prior cached state)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgeLogon threw", t)
            }
        } else if (op == "probebridgeclearrp") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                W.setSteamId(0x110000100C123456L)
                val ok1 = try { W.nativeDiagnosticSetRichPresence("status", "Playing") }
                          catch (_: UnsatisfiedLinkError) { false }
                val ok2 = try { W.nativeDiagnosticSetRichPresence("connect", "+1.2.3.4:27015") }
                          catch (_: UnsatisfiedLinkError) { false }
                try { W.nativeDiagnosticClearRichPresence() }
                catch (_: UnsatisfiedLinkError) {}
                Log.i(TAG, "probeBridgeClearRp: setOk=$ok1+$ok2, then Clear — " +
                    "look for 3 wn-cm-bridge 'set_rich_presence' lines: " +
                    "1 key, 2 keys, 0 keys (the clear)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgeClearRp threw", t)
            }
        } else if (op == "probebridgerp") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val aliceId = 76561197960287930L
                try { W.nativeDiagnosticRequestFriendRichPresence(aliceId) }
                catch (_: UnsatisfiedLinkError) {}
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "probeBridgeRp: cb depth pre=$depth0 post=$depth1 " +
                    "(expect post-pre=1 synthetic FriendRichPresenceUpdate_t; " +
                    "wn-cm-bridge log line: 'request_user_info(...alice, flags=0x800)')")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgeRp threw", t)
            }
        } else if (op == "probebridgeplayed") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                W.setAppId(0)        // baseline (already 0; no-op expected)
                W.setAppId(440)      // → broadcast 440
                W.setAppId(440)      // no-op (dedup)
                W.setAppId(0)        // → clear
                W.setAppId(570)      // → broadcast 570
                W.setAppId(0)        // → clear
                Log.i(TAG, "probeBridgePlayed: cycled setAppId 0→440→440→0→570→0 — " +
                    "look for 4 wn-cm-bridge 'notify_games_played' lines " +
                    "(440 broadcast, 0 clear, 570 broadcast, 0 clear)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgePlayed threw", t)
            }
        } else if (op == "probeauthwrap") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val testAppId = 99988  // avoid colliding with seedtestschemacache 99999
                val ownership = ByteArray(100) { ((it * 7 + 3) and 0xFF).toByte() }
                W.setAppId(testAppId)
                val inj = try { W.nativeDiagnosticInjectOwnershipTicket(testAppId, ownership) }
                          catch (_: UnsatisfiedLinkError) { false }
                val cachedLen = try { W.nativeDiagnosticGetCachedOwnershipTicket(testAppId, null) }
                                catch (_: UnsatisfiedLinkError) { -1 }
                val buf = ByteArray(256)
                val hCall = try { W.nativeDiagnosticGetAuthTicket(buf) }
                            catch (_: UnsatisfiedLinkError) { -1 }
                val sizePrefix = (buf[0].toInt() and 0xFF) or
                                  ((buf[1].toInt() and 0xFF) shl 8) or
                                  ((buf[2].toInt() and 0xFF) shl 16) or
                                  ((buf[3].toInt() and 0xFF) shl 24)
                val connId     = (buf[12].toInt() and 0xFF) or
                                  ((buf[13].toInt() and 0xFF) shl 8) or
                                  ((buf[14].toInt() and 0xFF) shl 16) or
                                  ((buf[15].toInt() and 0xFF) shl 24)
                val connCount  = (buf[20].toInt() and 0xFF) or
                                  ((buf[21].toInt() and 0xFF) shl 8) or
                                  ((buf[22].toInt() and 0xFF) shl 16) or
                                  ((buf[23].toInt() and 0xFF) shl 24)
                val firstOwnByte = buf[24].toInt() and 0xFF
                Log.i(TAG, "probeAuthWrap: inj=$inj cachedLen=$cachedLen hCall=$hCall " +
                    "sizePrefix=$sizePrefix connId=$connId connCount=$connCount " +
                    "firstOwnByte=$firstOwnByte " +
                    "(expect inj=true cachedLen=100 sizePrefix=20 connId=hCall " +
                    "connCount=1 firstOwnByte=3)")
                W.setAppId(0)
            } catch (t: Throwable) {
                Log.e(TAG, "probeAuthWrap threw", t)
            }
        } else if (op == "probebridgeticket") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val sizeOnly = try {
                    W.nativeDiagnosticGetCachedOwnershipTicket(440, null)
                } catch (_: UnsatisfiedLinkError) { -1 }
                val tooSmall = ByteArray(8)
                val sizeReport = try {
                    W.nativeDiagnosticGetCachedOwnershipTicket(440, tooSmall)
                } catch (_: UnsatisfiedLinkError) { -1 }
                val bigBuf = ByteArray(8192)
                val filled = try {
                    W.nativeDiagnosticGetCachedOwnershipTicket(440, bigBuf)
                } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "probeBridgeTicket: appId=440 sizeOnly=$sizeOnly " +
                    "tooSmall=$sizeReport bigBuf=$filled " +
                    "(expect all=0 until wn-session pre-fetches the ticket; " +
                    "non-zero means cache hit + bytes copied or size-needed)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgeTicket threw", t)
            }
        } else if (op == "probebridgereqbulk") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val sids = longArrayOf(
                    76561197960287932L,
                    76561197960287933L,
                    0L,                       // filtered
                    76561197960287934L,
                )
                val full = try {
                    W.nativeDiagnosticRequestUserInfoBulk(sids, flags = 0)  // default 0x47
                } catch (_: UnsatisfiedLinkError) { false }
                val nameOnly = try {
                    W.nativeDiagnosticRequestUserInfoBulk(sids, flags = 0x01)
                } catch (_: UnsatisfiedLinkError) { false }
                val empty = try {
                    W.nativeDiagnosticRequestUserInfoBulk(longArrayOf(), flags = 0)
                } catch (_: UnsatisfiedLinkError) { false }
                val allZero = try {
                    W.nativeDiagnosticRequestUserInfoBulk(longArrayOf(0L, 0L), flags = 0)
                } catch (_: UnsatisfiedLinkError) { false }
                Log.i(TAG, "probeBridgeReqBulk: full=$full nameOnly=$nameOnly " +
                    "empty=$empty allZero=$allZero " +
                    "(expect full=true nameOnly=true empty=false allZero=false; " +
                    "wn-cm-bridge lines should show count=3 for full/nameOnly)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgeReqBulk threw", t)
            }
        } else if (op == "probebridgereq") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val uncachedSid = 76561197960287932L
                val cachedSid   = 76561197960287930L  // possibly seeded by earlier seedtestfriends
                val r1 = try {
                    W.nativeDiagnosticRequestUserInformation(uncachedSid, nameOnly = false)
                } catch (_: UnsatisfiedLinkError) { false }
                val r2 = try {
                    W.nativeDiagnosticRequestUserInformation(uncachedSid, nameOnly = true)
                } catch (_: UnsatisfiedLinkError) { false }
                val r3 = try {
                    W.nativeDiagnosticRequestUserInformation(cachedSid, nameOnly = false)
                } catch (_: UnsatisfiedLinkError) { false }
                Log.i(TAG, "probeBridgeReq: " +
                    "uncached(fullSet)=$r1 uncached(nameOnly)=$r2 cached=$r3 " +
                    "(expect uncached=true=true cached=$r3 — look for wn-cm-bridge " +
                    "'request_user_info' log lines)")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgeReq threw", t)
            }
        } else if (op == "probebridgename") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val before = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                val unique = "BridgeProbe-${System.currentTimeMillis() % 100000}"
                val hCall = try {
                    W.nativeDiagnosticSetPersonaName(unique)
                } catch (_: UnsatisfiedLinkError) { 0L }
                val after = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "probeBridgeName: SetPersonaName('$unique') vtable[1] → " +
                    "hCall=$hCall cb depth before=$before after=$after " +
                    "(expect after-before=1 PersonaStateChange + check wn-cm-bridge " +
                    "log line: 'set_persona_name→ live CMClient')")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridgeName threw", t)
            }
        } else if (op == "probebridge") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                W.setPersonaState(0)  // baseline
                Thread.sleep(50)
                W.setPersonaState(1)  // online — should hit bridge
                Thread.sleep(50)
                W.setPersonaState(3)  // away — should hit bridge
                Log.i(TAG, "probeBridge: dispatched 3 setPersonaState calls — " +
                    "look for 'wn-cm-bridge' log lines (each call either logs " +
                    "'no active CMClient' or 'dispatched to live CMClient')")
            } catch (t: Throwable) {
                Log.e(TAG, "probeBridge threw", t)
            }
        } else if (op == "seedtestoverlay") {
            try {
                val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                val depth0 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.setGameOverlayActive(true)            // false→true: +1
                W.setGameOverlayActive(true)            // dedup: +0
                val depthMid = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                W.setGameOverlayActive(false)           // true→false: +1
                W.setGameOverlayActive(false)           // dedup: +0
                W.setGameOverlayActive(true)            // false→true: +1
                val depth1 = try { W.nativeDiagnosticCallbackDepth() } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "seedTestOverlay: cb depth pre=$depth0 mid=$depthMid post=$depth1 " +
                    "(expect mid-pre=1, post-mid=2 — 3 transitions, 2 redundant sets de-duped)")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestOverlay threw", t)
            }
        } else if (op == "seedteststats") {
            try {
                com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                    .setAchievementSchema(
                        apiNames    = arrayOf("ACH_TEST_FIRST",  "ACH_TEST_SECOND", "ACH_HIDDEN"),
                        displayNames = arrayOf("First Blood",    "Double Tap",      "???"),
                        descriptions = arrayOf("Score one kill.", "Score two kills.", "Reach the hidden ending."),
                        icons        = arrayOf("",                "",                ""),
                        hidden       = booleanArrayOf(false, false, true),
                    )
                com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                    .setAchievementProgress("ACH_TEST_FIRST", achieved = true, unlockTimeUnix = 1700000000)
                com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                    .addAchievementLocale("ACH_TEST_FIRST", "spanish",
                        displayName = "Primera Sangre",
                        description = "Consigue una eliminación.")
                val cbDepthAfterSchema = try {
                    com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                        .nativeDiagnosticCallbackDepth()
                } catch (_: UnsatisfiedLinkError) { -1 }
                val setOk = try {
                    com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                        .nativeDiagnosticSetAchievement("ACH_TEST_SECOND")
                } catch (_: UnsatisfiedLinkError) { false }
                val storeOk = try {
                    com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                        .nativeDiagnosticStoreStats()
                } catch (_: UnsatisfiedLinkError) { false }
                val cbDepthAfterStore = try {
                    com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                        .nativeDiagnosticCallbackDepth()
                } catch (_: UnsatisfiedLinkError) { -1 }
                val progressOk = try {
                    com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                        .nativeDiagnosticIndicateAchievementProgress(
                            "ACH_HIDDEN", current = 50, max = 100)
                } catch (_: UnsatisfiedLinkError) { false }
                val cbDepthAfterProgress = try {
                    com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                        .nativeDiagnosticCallbackDepth()
                } catch (_: UnsatisfiedLinkError) { -1 }
                Log.i(TAG, "seedTestStats: pushed 3 synthetic achievements " +
                    "(cb depth post-schema=$cbDepthAfterSchema, " +
                    "setAch ok=$setOk, storeStats ok=$storeOk, " +
                    "cb depth post-store=$cbDepthAfterStore, " +
                    "indicateProgress ok=$progressOk, " +
                    "cb depth post-progress=$cbDepthAfterProgress)")
            } catch (t: Throwable) {
                Log.e(TAG, "seedTestStats threw", t)
            }
        } else if (op != "query" && op != "state" && after != before) {
            PrefManager.wnHybridMode = after
            try {
                SteamService.setHybridModeRuntime(after)
            } catch (t: Throwable) {
                Log.e(TAG, "setHybridModeRuntime threw", t)
            }
        }

        Log.i(
            TAG,
            "op=$op before=$before after=$after loggedIn=${SteamService.isLoggedIn}" +
                " bootstrapAlive=$bsInitialized steamId=${WnSteamBootstrap.steamId()}" +
                " persona='${WnSteamBootstrap.personaName() ?: ""}'",
        )

        if (op == "state") {
            val bs = WnSteamBootstrap
            val W = com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
            val pSid     = runCatching { W.nativeGetPushedSteamId() }.getOrNull() ?: 0L
            val pName    = runCatching { W.nativeGetPushedPersonaName() }.getOrNull() ?: ""
            val pPState  = runCatching { W.nativeGetPushedPersonaState() }.getOrNull() ?: 0
            val pLoggedOn= runCatching { W.nativeGetPushedLoggedOn() }.getOrNull() ?: false
            val pAppId   = runCatching { W.nativeGetPushedAppId() }.getOrNull() ?: 0
            val pOwned   = runCatching { W.nativeGetPushedOwnedAppCount() }.getOrNull() ?: 0
            val pInst    = runCatching { W.nativeGetPushedInstalledAppCount() }.getOrNull() ?: 0
            val pFriends = runCatching { W.nativeGetPushedFriendCount() }.getOrNull() ?: 0
            val pCFiles  = runCatching { W.nativeGetPushedCloudFileCount() }.getOrNull() ?: 0
            val pCAcct   = runCatching { W.nativeGetPushedCloudEnabledAccount() }.getOrNull() ?: false
            val pCApp    = runCatching { W.nativeGetPushedCloudEnabledApp() }.getOrNull() ?: false
            val pETkt    = if (pAppId > 0) runCatching {
                W.nativeGetPushedEncryptedAppTicketSize(pAppId)
            }.getOrNull() ?: 0 else 0
            val pIpCountry = runCatching { W.nativeGetPushedIpCountry() }.getOrNull() ?: ""
            Log.i(TAG, "pushed: sid=$pSid pname='$pName' pstate=$pPState " +
                "loggedOn=$pLoggedOn appId=$pAppId owned=$pOwned installed=$pInst " +
                "friends=$pFriends cloudFiles=$pCFiles cloudAcct=$pCAcct cloudApp=$pCApp " +
                "ip='$pIpCountry' encTicketBytes=$pETkt")
            val sb = StringBuilder()
            sb.append("user(bootstrap): liveSid=").append(bs.liveSteamId())
                .append(" pname='").append(bs.personaName() ?: "").append('\'')
                .append(" pstate=").append(bs.personaState())
                .append(" loggedOnPub=").append(bs.loggedOnPublic())
            Log.i(TAG, sb.toString())

            val wnState = com.winlator.cmod.feature.stores.steam.service
                .SteamService.wnSessionStateForDiag()
            val wnStateLabel = when (wnState) {
                -1 -> "noSession"
                0 -> "Disconnected"
                1 -> "Connecting"
                2 -> "Connected"
                3 -> "LoggedOn"
                else -> "raw=$wnState"
            }
            val suspendReason = com.winlator.cmod.feature.stores.steam.service
                .SteamService.wnSessionSuspensionReasonForDiag()
            Log.i(TAG, "wn-session: state=$wnState ($wnStateLabel) " +
                "logonGateUntilMs=${com.winlator.cmod.feature.stores.steam.service.SteamService.logonGateUntilMs} " +
                "lastEResult=${com.winlator.cmod.feature.stores.steam.service.SteamService.lastLogonFailureEresult} " +
                "consecutive=${com.winlator.cmod.feature.stores.steam.service.SteamService.consecutiveLogonFailures} " +
                "suspend=[$suspendReason]")

            val appId = bs.currentAppId()
            sb.setLength(0)
            sb.append("apps(bootstrap): boundAppId=").append(appId)
                .append(" subscribed=").append(bs.isSubscribedApp(appId))
                .append(" installed=").append(bs.isAppInstalled(appId))
                .append(" lang=").append(bs.currentGameLanguage() ?: "")
                .append(" dlcCount=").append(bs.dlcCount(appId))
                .append(" owner=").append(bs.appOwner())
                .append(" famShared=").append(bs.isSubscribedFromFamilySharing())
            Log.i(TAG, sb.toString())

            sb.setLength(0)
            sb.append("cloud(bootstrap): account=").append(bs.cloudEnabledForAccount())
                .append(" app=").append(bs.cloudEnabledForApp())
                .append(" files=").append(bs.cloudFileCount())
                .append(" quota=").append(bs.cloudQuota().joinToString("/"))
            Log.i(TAG, sb.toString())

            val achList = bs.listAchievements()
            val directFromPushed = try {
                com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                    .nativeDiagnosticAchievementCount()
            } catch (_: UnsatisfiedLinkError) { -1 }
            val cbDepth = try {
                com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                    .nativeDiagnosticCallbackDepth()
            } catch (_: UnsatisfiedLinkError) { -1 }
            sb.setLength(0)
            sb.append("stats: numAch=").append(bs.numAchievements())
                .append(" listLen=").append(achList.size)
                .append(" directPushed=").append(directFromPushed)
                .append(" cbQueueDepth=").append(cbDepth)
                .append(" firstAch=").append(achList.firstOrNull() ?: "")
            Log.i(TAG, sb.toString())

            val friends = bs.listFriends()
            val pushedFriendCount = runCatching { W.nativeGetPushedFriendCount() }.getOrNull() ?: 0
            val pushedFirstFriend = runCatching { W.nativeGetPushedFirstFriend() }.getOrNull() ?: 0L
            sb.setLength(0)
            sb.append("friends: immediateCount=")
                .append(bs.friendCount(WnSteamBootstrap.FriendFlags.Immediate))
                .append(" listLen=").append(friends.size)
                .append(" firstId=").append(friends.firstOrNull() ?: 0)
                .append(" (pushed=").append(pushedFriendCount)
                .append(" firstSid=").append(pushedFirstFriend).append(")")
            Log.i(TAG, sb.toString())

            val pushedServerRealTime = runCatching { W.nativeGetPushedServerRealTime() }.getOrNull() ?: 0
            val pushedIpCountryUtil  = runCatching { W.nativeGetPushedIpCountry() }.getOrNull() ?: ""
            val pushedUiLang         = runCatching { W.nativeGetPushedUiLanguage() }.getOrNull() ?: ""
            sb.setLength(0)
            sb.append("utils: serverTime=").append(bs.serverRealTime())
                .append(" ipCountry=").append(bs.ipCountry() ?: "")
                .append(" uiLang=").append(bs.steamUiLanguage() ?: "")
                .append(" battery=").append(bs.currentBatteryPower())
                .append(" (pushed: time=").append(pushedServerRealTime)
                .append(" ip='").append(pushedIpCountryUtil).append("'")
                .append(" lang='").append(pushedUiLang).append("')")
            Log.i(TAG, sb.toString())

            val tcpAccepted = try {
                com.winlator.cmod.feature.stores.steam.wnsteam.WnLibSteamClient
                    .nativeDiagnosticTcpAccepted()
            } catch (_: UnsatisfiedLinkError) { -1 }
            Log.i(TAG, "tcp: accepted=$tcpAccepted")
        }
    }

    companion object {
        const val ACTION = "com.winnative.cmod.action.HYBRID_MODE"
        private const val TAG = "HybridModeReceiver"
    }
}
