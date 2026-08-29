package com.winlator.cmod.app.shell

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreGameDetailBranchPickerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val branches =
        listOf(
            StoreBranchOption(id = "public", label = "Default (public)", buildId = 100L, isInstalled = true),
            StoreBranchOption(id = "beta", label = "beta", buildId = 200L),
            StoreBranchOption(id = "legacy", label = "legacy", buildId = 50L),
        )

    private fun setScreen(
        branchOptions: List<StoreBranchOption>,
        selectedBranchId: String = "public",
        isInstalled: Boolean = false,
        onSelectBranch: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            StoreGameDetailScreen(
                title = "Test Game",
                subtitle = "Test Studio",
                sourceLabel = "Steam",
                heroImageUrl = null,
                isLoading = false,
                isInstalled = isInstalled,
                installPathDisplay = "/storage/emulated/0/WinNative",
                downloadSize = 1_000_000L,
                installSize = 2_000_000L,
                availableBytes = 900_000_000L,
                isInstallEnabled = true,
                customPathLabel = "Custom",
                showUpdateCheck = true,
                branches = branchOptions,
                selectedBranchId = selectedBranchId,
                onSelectBranch = onSelectBranch,
                onBack = {},
            )
        }
        composeRule.waitForIdle()
    }

    private fun downloadButtonBounds(): Rect =
        composeRule
            .onNodeWithText("Download", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

    @Test
    fun branchPickerDoesNotMoveTheDownloadButton() {
        val shown = mutableStateOf(false)
        composeRule.setContent {
            StoreGameDetailScreen(
                title = "Test Game",
                subtitle = "Test Studio",
                sourceLabel = "Steam",
                heroImageUrl = null,
                isLoading = false,
                isInstalled = false,
                installPathDisplay = "/storage/emulated/0/WinNative",
                downloadSize = 1_000_000L,
                installSize = 2_000_000L,
                availableBytes = 900_000_000L,
                isInstallEnabled = true,
                customPathLabel = "Custom",
                branches = if (shown.value) branches else emptyList(),
                selectedBranchId = "public",
                onBack = {},
            )
        }
        composeRule.waitForIdle()
        val withoutPicker = downloadButtonBounds()

        shown.value = true
        composeRule.waitForIdle()
        val withPicker = downloadButtonBounds()

        composeRule.onNodeWithText("Default (public)", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(withoutPicker.left, withPicker.left, 0.5f)
        assertEquals(withoutPicker.top, withPicker.top, 0.5f)
        assertEquals(withoutPicker.bottom, withPicker.bottom, 0.5f)
        assertEquals(withoutPicker.right, withPicker.right, 0.5f)
    }

    @Test
    fun branchPickerSitsAboveTheDownloadButton() {
        setScreen(branchOptions = branches)

        val picker =
            composeRule
                .onNodeWithText("Default (public)", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        assert(picker.bottom <= downloadButtonBounds().top) {
            "branch picker (bottom=${picker.bottom}) should sit above the download button " +
                "(top=${downloadButtonBounds().top})"
        }
    }

    @Test
    fun branchPickerShowsTheSelectedBranch() {
        setScreen(branchOptions = branches, selectedBranchId = "beta")

        composeRule.onNodeWithText("beta", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun branchPickerIsHiddenWhenOnlyOneBranchExists() {
        setScreen(branchOptions = branches.take(1))

        composeRule.onNodeWithText("VERSION", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("PUBLIC", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun branchTagSitsBetweenTheBackButtonAndTheSourceTag() {
        setScreen(branchOptions = branches)

        val tag = composeRule.onNodeWithText("PUBLIC", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val source = composeRule.onNodeWithText("STEAM", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assert(tag.right <= source.left) {
            "branch tag (right=${tag.right}) should sit left of the Steam tag (left=${source.left})"
        }
        assert(tag.top < downloadButtonBounds().top) {
            "branch tag should be in the top bar, not the action column"
        }
    }

    @Test
    fun branchTagStaysAvailableForAnInstalledGameWithNoDownloadCta() {
        setScreen(branchOptions = branches, isInstalled = true)

        composeRule.onNodeWithText("PUBLIC", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Download", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("VERSION", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun choosingABranchFromTheTagReportsTheSelection() {
        var chosen: String? = null
        setScreen(branchOptions = branches, isInstalled = true, onSelectBranch = { chosen = it })

        composeRule.onNodeWithText("PUBLIC", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("beta", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals("beta", chosen)
    }

    @Test
    fun choosingABranchReportsTheSelection() {
        var chosen: String? = null
        setScreen(branchOptions = branches, onSelectBranch = { chosen = it })

        composeRule.onNodeWithText("Default (public)", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("legacy", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals("legacy", chosen)
    }
}
