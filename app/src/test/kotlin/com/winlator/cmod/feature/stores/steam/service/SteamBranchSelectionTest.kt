package com.winlator.cmod.feature.stores.steam.service

import com.winlator.cmod.feature.stores.steam.data.BranchInfo
import com.winlator.cmod.feature.stores.steam.data.ManifestInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamBranchSelectionTest {
    private fun branches(vararg entries: Pair<String, BranchInfo>): Map<String, BranchInfo> = mapOf(*entries)

    private fun branch(
        name: String,
        buildId: Long,
        pwdRequired: Boolean = false,
    ) = name to BranchInfo(name = name, buildId = buildId, pwdRequired = pwdRequired)

    @Test
    fun selectableBranchesPutsPublicFirstAndSortsTheRest() {
        val result =
            SteamBranchSelection.selectableBranches(
                branches(
                    branch("beta", 200L),
                    branch("public", 100L),
                    branch("alpha", 300L),
                ),
            )

        assertEquals(listOf("public", "alpha", "beta"), result.map { it.name })
    }

    @Test
    fun selectableBranchesDropsPasswordProtectedBranches() {
        val result =
            SteamBranchSelection.selectableBranches(
                branches(
                    branch("public", 100L),
                    branch("internal", 400L, pwdRequired = true),
                ),
            )

        assertEquals(listOf("public"), result.map { it.name })
    }

    @Test
    fun selectableBranchesNamesEntriesFromTheirKeyWhenTheValueHasNone() {
        val result =
            SteamBranchSelection.selectableBranches(
                mapOf("beta" to BranchInfo(name = "", buildId = 7L)),
            )

        assertEquals(listOf("beta"), result.map { it.name })
    }

    @Test
    fun selectableBranchesIsEmptyWhenNoMetadataIsLoaded() {
        assertTrue(SteamBranchSelection.selectableBranches(emptyMap()).isEmpty())
    }

    @Test
    fun resolveBranchDefaultsToPublicWhenNothingIsStored() {
        val available = branches(branch("public", 100L), branch("beta", 200L))

        assertEquals("public", SteamBranchSelection.resolveBranch(null, available))
        assertEquals("public", SteamBranchSelection.resolveBranch("", available))
        assertEquals("public", SteamBranchSelection.resolveBranch("   ", available))
    }

    @Test
    fun resolveBranchKeepsAStoredBranchThatStillExists() {
        val available = branches(branch("public", 100L), branch("beta", 200L))

        assertEquals("beta", SteamBranchSelection.resolveBranch("beta", available))
        assertEquals("beta", SteamBranchSelection.resolveBranch(" BETA ", available))
    }

    @Test
    fun resolveBranchFallsBackWhenTheStoredBranchDisappeared() {
        val available = branches(branch("public", 100L))

        assertEquals("public", SteamBranchSelection.resolveBranch("retired-beta", available))
    }

    @Test
    fun resolveBranchFallsBackWhenTheStoredBranchNeedsAPassword() {
        val available = branches(branch("public", 100L), branch("internal", 400L, pwdRequired = true))

        assertEquals("public", SteamBranchSelection.resolveBranch("internal", available))
    }

    @Test
    fun resolveBranchKeepsTheStoredValueWhileMetadataIsStillLoading() {
        assertEquals("beta", SteamBranchSelection.resolveBranch("beta", emptyMap()))
    }

    @Test
    fun buildIdForBranchPrefersTheRequestedBranchThenPublic() {
        val available = branches(branch("public", 100L), branch("beta", 200L))

        assertEquals(200L, SteamBranchSelection.buildIdForBranch(available, "beta"))
        assertEquals(200L, SteamBranchSelection.buildIdForBranch(available, "BETA"))
        assertEquals(100L, SteamBranchSelection.buildIdForBranch(available, "missing"))
        assertEquals(0L, SteamBranchSelection.buildIdForBranch(emptyMap(), "beta"))
    }

    @Test
    fun installedBuildIdPrefersTheRecordedValueOverTheLatestBuild() {
        val available = branches(branch("public", 900L))

        assertEquals(500L, SteamBranchSelection.installedBuildId(500L, available, "public"))
    }

    @Test
    fun installedBuildIdFallsBackToTheBranchBuildForLegacyInstalls() {
        val available = branches(branch("public", 900L))

        assertEquals(900L, SteamBranchSelection.installedBuildId(0L, available, "public"))
    }

    @Test
    fun installedManifestPrefersTheManifestRecordedOnDisk() {
        val latest = ManifestInfo(name = "public", gid = 999L, size = 10L, download = 5L)

        val manifest = SteamBranchSelection.installedManifest(latest, onDiskManifestId = 111L)

        assertEquals(111L, manifest?.gid)
        assertEquals(10L, manifest?.size)
    }

    @Test
    fun installedManifestKeepsTheResolvedEntryWhenDiskAgrees() {
        val latest = ManifestInfo(name = "public", gid = 111L, size = 10L, download = 5L)

        assertEquals(latest, SteamBranchSelection.installedManifest(latest, onDiskManifestId = 111L))
    }

    @Test
    fun installedManifestSynthesisesAnEntryWhenOnlyDiskKnowsTheManifest() {
        val manifest = SteamBranchSelection.installedManifest(null, onDiskManifestId = 111L, branch = "beta")

        assertEquals(111L, manifest?.gid)
        assertEquals("beta", manifest?.name)
    }

    @Test
    fun installedManifestIgnoresInProgressAndMissingDiskMarkers() {
        val latest = ManifestInfo(name = "public", gid = 999L, size = 10L, download = 5L)

        assertEquals(latest, SteamBranchSelection.installedManifest(latest, onDiskManifestId = Long.MAX_VALUE))
        assertEquals(latest, SteamBranchSelection.installedManifest(latest, onDiskManifestId = 0L))
        assertEquals(latest, SteamBranchSelection.installedManifest(latest, onDiskManifestId = null))
    }

    @Test
    fun installedManifestIsNullWhenNeitherSideKnowsAManifest() {
        assertNull(SteamBranchSelection.installedManifest(null, onDiskManifestId = null))
        assertNull(SteamBranchSelection.installedManifest(null, onDiskManifestId = 0L))
    }

    @Test
    fun staleInstallIsDetectedWhenTheBranchMovedOn() {
        val available = branches(branch("public", 900L))

        assertTrue(SteamBranchSelection.isInstallStale(500L, available, "public"))
        assertFalse(SteamBranchSelection.isInstallStale(900L, available, "public"))
        assertFalse(SteamBranchSelection.isInstallStale(0L, available, "public"))
        assertFalse(SteamBranchSelection.isInstallStale(500L, emptyMap(), "public"))
    }
}
