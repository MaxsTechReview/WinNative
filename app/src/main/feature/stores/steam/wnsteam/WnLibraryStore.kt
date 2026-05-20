package com.winlator.cmod.feature.stores.steam.wnsteam

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Kotlin facade over the native [WnSteamSession]'s library store. Holds the
 * latest [WnLibrarySnapshot] and exposes a [SharedFlow] that re-emits each
 * time the native side fires its observer. Parsing happens on the
 * dispatcher of whoever calls [refresh] — typically a background coroutine.
 *
 * Usage:
 *   val library = WnLibraryStore(session)
 *   library.startObserving()           // hooks up native observer
 *   library.snapshots.collect { snap -> /* render UI */ }
 *
 * Snapshot reads are cheap (a JSON copy of the store), so refreshing
 * eagerly per native callback is fine for libraries up to a few thousand
 * apps. Larger libraries would benefit from a diff-based observer.
 */
class WnLibraryStore(private val session: WnSteamSession) {

    private val _snapshots =
        MutableSharedFlow<WnLibrarySnapshot>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** Hot flow of library snapshots. Latest value is replayed to new collectors. */
    val snapshots: SharedFlow<WnLibrarySnapshot> = _snapshots.asSharedFlow()

    private val nativeObserver = WnLibraryObserver { refresh() }

    /** Last snapshot — synchronous accessor for callers that can't suspend. */
    @Volatile var current: WnLibrarySnapshot = WnLibrarySnapshot.EMPTY
        private set

    /** Wire the native observer and emit the initial snapshot. */
    fun startObserving() {
        session.setLibraryObserver(nativeObserver)
        refresh()
    }

    fun stopObserving() {
        session.setLibraryObserver(null)
    }

    /** Read + parse a fresh snapshot from native and emit it. */
    fun refresh() {
        val json = session.getLibrarySnapshotJson()
        val parsed = runCatching { parseSnapshot(json) }
            .onFailure { Timber.tag(TAG).w(it, "snapshot parse failed; json=%s",
                                            json.take(200)) }
            .getOrDefault(WnLibrarySnapshot.EMPTY)
        current = parsed
        _snapshots.tryEmit(parsed)
    }

    companion object {
        private const val TAG = "WnLibraryStore"

        @JvmStatic
        fun parseSnapshot(json: String): WnLibrarySnapshot {
            if (json.isBlank() || json == "{}") return WnLibrarySnapshot.EMPTY
            val root = JSONObject(json)
            val packages = root.optJSONArray("packages")?.let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    WnOwnedPackage(
                        id           = o.getInt("id"),
                        licenseFlags = o.optInt("flags"),
                        licenseType  = o.optInt("license_type"),
                        changeNumber = o.optInt("change_number"),
                        accessToken  = o.optString("access_token", "0"),
                    )
                }
            } ?: emptyList()
            val ownedApps = root.optJSONArray("owned_apps")?.let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    WnOwnedApp(
                        id               = o.getInt("id"),
                        name             = o.optString("name"),
                        type             = o.optString("type"),
                        sortAs           = o.optString("sort_as"),
                        osList           = o.optString("os_list"),
                        parentAppId      = o.optInt("parent"),
                        changeNumber     = o.optInt("change_number"),
                        accessToken      = o.optString("access_token", "0"),
                        dlcAppIds        = o.optJSONArray("dlc").toIntList(),
                        sourcePackageIds = o.optJSONArray("src_packages").toIntList(),
                    )
                }
            } ?: emptyList()
            return WnLibrarySnapshot(
                packages       = packages,
                ownedApps      = ownedApps,
                allAppsCount   = root.optInt("all_apps_count"),
                ownedAppsCount = root.optInt("owned_apps_count"),
            )
        }

        private fun JSONArray?.toIntList(): List<Int> {
            if (this == null) return emptyList()
            return List(length()) { i -> getInt(i) }
        }
    }
}
