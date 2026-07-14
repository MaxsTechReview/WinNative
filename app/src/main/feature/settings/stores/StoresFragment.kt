// Settings > Stores fragment — hosts StoresScreen via ComposeView.
package com.winlator.cmod.feature.settings
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.winlator.cmod.R
import com.winlator.cmod.app.shell.UnifiedActivity
import com.winlator.cmod.feature.stores.epic.service.EpicAuthManager
import com.winlator.cmod.feature.stores.epic.ui.auth.EpicOAuthActivity
import com.winlator.cmod.feature.stores.gog.service.GOGAuthManager
import com.winlator.cmod.feature.stores.gog.service.GOGService
import com.winlator.cmod.feature.stores.gog.ui.auth.GOGOAuthActivity
import com.winlator.cmod.feature.stores.steam.SteamLoginActivity
import com.winlator.cmod.feature.stores.steam.enums.Language
import com.winlator.cmod.feature.setup.SetupWizardActivity
import com.winlator.cmod.feature.stores.steam.service.SteamService
import com.winlator.cmod.feature.stores.steam.utils.PrefManager
import com.winlator.cmod.feature.stores.ubisoft.UbisoftConnect
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.content.component.ComponentInstaller
import com.winlator.cmod.shared.android.DirectoryPickerDialog
import com.winlator.cmod.shared.io.AssetPaths
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.theme.WinNativeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class StoresFragment : Fragment() {
    private var storeState by mutableStateOf(StoreState())
    private lateinit var serverOptions: List<Pair<Int, String>>

    private val gogLoginLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val code = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_AUTH_CODE)
                if (!code.isNullOrBlank()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val authResult = GOGAuthManager.authenticateWithCode(requireContext(), code)
                        if (authResult.isSuccess) {
                            GOGService.start(requireContext())
                        }
                        refresh()
                    }
                }
            }
        }

    private val epicLoginLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val code = result.data?.getStringExtra(EpicOAuthActivity.EXTRA_AUTH_CODE)
                if (!code.isNullOrBlank()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        EpicAuthManager.authenticateWithCode(requireContext(), code)
                        refresh()
                    }
                } else {
                    refresh()
                }
            } else {
                refresh()
            }
        }

    private val steamLoginLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            refresh()
        }

    // Lifecycle
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.stores_accounts_title)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        PrefManager.init(requireContext())
        serverOptions = loadServerOptions()
        refresh()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WinNativeTheme(
                    colorScheme =
                        darkColorScheme(
                            primary = Color(0xFF1A9FFF),
                            background = Color(0xFF141B24),
                            surface = Color(0xFF1E252E),
                        ),
                ) {
                    StoresScreen(
                        state = storeState,
                        serverOptions = serverOptions,
                        onSteamSignIn = { steamLoginLauncher.launch(Intent(requireContext(), SteamLoginActivity::class.java)) },
                        onSteamSignOut = {
                            SteamService.logOut()
                            refresh()
                        },
                        onEpicSignIn = { epicLoginLauncher.launch(Intent(requireContext(), EpicOAuthActivity::class.java)) },
                        onEpicSignOut = {
                            EpicAuthManager.logoutSync(requireContext())
                            refresh()
                        },
                        onGogSignIn = { gogLoginLauncher.launch(Intent(requireContext(), GOGOAuthActivity::class.java)) },
                        onGogSignOut = {
                            CoroutineScope(Dispatchers.Main).launch {
                                GOGService.logout(requireContext())
                                refresh()
                            }
                        },
                        onUbisoftInstall = { installUbisoftConnect() },
                        onSharedFolderChanged = {
                            PrefManager.useSingleDownloadFolder = it
                            refresh()
                        },
                        onDownloadSpeedChanged = {
                            PrefManager.downloadSpeed = it
                            refresh()
                        },
                        onDownloadServerChanged = { cellId ->
                            PrefManager.cellId = cellId
                            PrefManager.cellIdManuallySet = cellId != 0
                            refresh()
                        },
                        onPickDefaultFolder = { pickFolder(PrefManager.defaultDownloadFolder) { PrefManager.defaultDownloadFolder = it } },
                        onPickSteamFolder = { pickFolder(PrefManager.steamDownloadFolder) { PrefManager.steamDownloadFolder = it } },
                        onPickEpicFolder = { pickFolder(PrefManager.epicDownloadFolder) { PrefManager.epicDownloadFolder = it } },
                        onPickGogFolder = { pickFolder(PrefManager.gogDownloadFolder) { PrefManager.gogDownloadFolder = it } },
                        onContainerLanguageSelected = { index ->
                            val langName = Language.containerLangForIndex(index)
                            PrefManager.containerLanguage = langName
                            refresh()
                        },
                        bridge = (requireActivity() as? UnifiedActivity)?.settingsNavBridge,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // Helpers
    private fun refresh() {
        val ctx = context ?: return
        val prev = storeState
        val containerLanguageLabels = Language.displayLabels()
        val containerLanguageIndex = Language.indexForContainerLang(PrefManager.containerLanguage)
        storeState =
            StoreState(
                isSteamLoggedIn = SteamService.isLoggedIn,
                isEpicLoggedIn = EpicAuthManager.isLoggedIn(ctx),
                isGogLoggedIn = GOGAuthManager.isLoggedIn(ctx),
                sharedFolder = PrefManager.useSingleDownloadFolder,
                downloadSpeed = PrefManager.downloadSpeed,
                downloadServer = PrefManager.cellId,
                downloadServerManuallySet = PrefManager.cellIdManuallySet,
                defaultFolder = resolveUri(PrefManager.defaultDownloadFolder, ctx),
                steamFolder = resolveUri(PrefManager.steamDownloadFolder, ctx),
                epicFolder = resolveUri(PrefManager.epicDownloadFolder, ctx),
                gogFolder = resolveUri(PrefManager.gogDownloadFolder, ctx),
                containerLanguageLabels = containerLanguageLabels,
                containerLanguageIndex = containerLanguageIndex,
                isUbisoftInstalled = UbisoftConnect.isInstalled(ctx),
                // Preserve the in-flight download/install state across refreshes.
                ubisoftBusy = prev.ubisoftBusy,
                ubisoftStatus = prev.ubisoftStatus,
            )
    }

    // The installer runs in the user's default container; the install lands in the shared store
    // (shared by every container), so which container runs it doesn't matter.
    private fun defaultUbisoftContainer(ctx: android.content.Context): Container? =
        try {
            val cm = ContainerManager(ctx)
            cm.getContainerById(SetupWizardActivity.getDefaultX86ContainerId(ctx))
                ?: cm.getContainers().firstOrNull()
        } catch (_: Exception) {
            null
        }

    // Download + silently install Ubisoft Connect (lands in the shared store, shared by all containers).
    private fun installUbisoftConnect() {
        val ctx = context ?: return
        if (storeState.ubisoftBusy) return
        val container = defaultUbisoftContainer(ctx)
        if (container == null) {
            Toast.makeText(ctx, R.string.stores_accounts_ubisoft_no_container, Toast.LENGTH_SHORT).show()
            return
        }
        val appCtx = ctx.applicationContext
        storeState = storeState.copy(ubisoftBusy = true, ubisoftStatus = "")
        CoroutineScope(Dispatchers.IO).launch {
            val listener =
                object : ComponentInstaller.Listener {
                    override fun onStatus(text: String) = postUbisoftStatus(text)

                    override fun onProgress(fraction: Float) {
                        if (fraction in 0f..1f) postUbisoftStatus("Downloading ${(fraction * 100).toInt()}%")
                    }
                }
            val error =
                try {
                    ComponentInstaller(appCtx, container, "ubisoft-connect", UbisoftConnect.installManifest(), listener).run()
                    null
                } catch (e: Exception) {
                    e.message ?: "error"
                }
            withContext(Dispatchers.Main) {
                storeState = storeState.copy(ubisoftBusy = false, ubisoftStatus = "")
                if (error != null) {
                    Toast.makeText(
                        appCtx,
                        appCtx.getString(R.string.stores_accounts_ubisoft_install_failed, error),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                refresh()
            }
        }
    }

    private fun postUbisoftStatus(text: String) {
        view?.post {
            if (storeState.ubisoftBusy) storeState = storeState.copy(ubisoftStatus = text)
        }
    }

    private fun loadServerOptions(): List<Pair<Int, String>> =
        try {
            val json =
                requireContext()
                    .assets
                    .open(AssetPaths.STEAM_REGIONS)
                    .bufferedReader()
                    .use { it.readText() }
            val obj = JSONObject(json)
            obj
                .keys()
                .asSequence()
                .mapNotNull { key -> key.toIntOrNull()?.let { id -> id to obj.getString(key) } }
                .sortedBy { it.first }
                .toList()
        } catch (_: Exception) {
            listOf(0 to "Automatic")
        }

    private fun resolveUri(
        uriStr: String,
        ctx: android.content.Context,
    ): String {
        if (uriStr.isEmpty()) return ""
        return try {
            FileUtils.getFilePathFromUri(ctx, Uri.parse(uriStr)) ?: uriStr
        } catch (_: Exception) {
            uriStr
        }
    }

    private fun pickFolder(
        currentValue: String,
        onPicked: (String) -> Unit,
    ) {
        val hostActivity = activity ?: return
        val currentPath = resolveUri(currentValue, hostActivity).ifBlank { null }
        DirectoryPickerDialog.show(
            activity = hostActivity,
            initialPath = currentPath,
            title = getString(R.string.settings_content_install_directory),
        ) { path ->
            onPicked(Uri.fromFile(java.io.File(path)).toString())
            refresh()
        }
    }
}
