package com.winlator.cmod.feature.stores.steam.wnsteam

/**
 * Mirror of the native `OwnedPackage` struct — one of the user's Steam
 * licenses (subscription/package). [accessToken] is encoded as decimal
 * string since it's a uint64 and JSON has no native unsigned 64-bit type.
 */
data class WnOwnedPackage(
    val id: Int,
    val licenseFlags: Int,
    val licenseType: Int,
    val changeNumber: Int,
    val accessToken: String,
)

/**
 * Mirror of the native `OwnedApp` struct. Only apps with at least one
 * source package are included in [WnLibrarySnapshot.ownedApps]; parent
 * stubs (apps known only because the user owns DLC of them) are
 * excluded.
 */
data class WnOwnedApp(
    val id: Int,
    val name: String,
    val type: String,
    val sortAs: String,
    val osList: String,
    val parentAppId: Int,
    val changeNumber: Int,
    val accessToken: String,
    val dlcAppIds: List<Int>,
    val sourcePackageIds: List<Int>,
)

/** Full snapshot of the native library store. */
data class WnLibrarySnapshot(
    val packages: List<WnOwnedPackage>,
    val ownedApps: List<WnOwnedApp>,
    val allAppsCount: Int,
    val ownedAppsCount: Int,
) {
    companion object {
        val EMPTY = WnLibrarySnapshot(emptyList(), emptyList(), 0, 0)
    }
}
