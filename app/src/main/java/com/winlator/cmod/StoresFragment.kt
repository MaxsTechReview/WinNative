/* Settings > Stores fragment — hosts StoresScreen via ComposeView. */
package com.winlator.cmod

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
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
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.epic.service.EpicAuthManager
import com.winlator.cmod.epic.ui.auth.EpicOAuthActivity
import com.winlator.cmod.gog.service.GOGAuthManager
import com.winlator.cmod.gog.service.GOGService
import com.winlator.cmod.gog.ui.auth.GOGOAuthActivity
import com.winlator.cmod.steam.SteamLoginActivity
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.utils.PrefManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class StoresFragment : Fragment() {

    private var storeState by mutableStateOf(StoreState())
    private lateinit var serverOptions: List<Pair<Int, String>>

    // Folder-picker launchers
    private val defaultFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { persistUri(it); PrefManager.defaultDownloadFolder = it.toString(); refresh() } }

    private val steamFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { persistUri(it); PrefManager.steamDownloadFolder = it.toString(); refresh() } }

    private val epicFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { persistUri(it); PrefManager.epicDownloadFolder = it.toString(); refresh() } }

    private val gogFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { persistUri(it); PrefManager.gogDownloadFolder = it.toString(); refresh() } }

    private val amazonFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { persistUri(it); PrefManager.amazonDownloadFolder = it.toString(); refresh() } }

    private val gogLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
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

    private val epicLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
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

    private val steamLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refresh()
    }

    // Lifecycle
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.stores)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        PrefManager.init(requireContext())
        serverOptions = loadServerOptions()
        refresh()

        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary    = Color(0xFF1A9FFF),
                        background = Color(0xFF0D1117),
                        surface    = Color(0xFF161B22),
                    )
                ) {
                    StoresScreen(
                        state                = storeState,
                        serverOptions        = serverOptions,
                        onSteamSignIn        = { steamLoginLauncher.launch(Intent(requireContext(), SteamLoginActivity::class.java)) },
                        onSteamSignOut       = {
                            SteamService.logOut()
                            refresh()
                        },
                        onEpicSignIn         = { epicLoginLauncher.launch(Intent(requireContext(), EpicOAuthActivity::class.java)) },
                        onEpicSignOut        = { EpicAuthManager.logoutSync(requireContext()); refresh() },
                        onGogSignIn          = { gogLoginLauncher.launch(Intent(requireContext(), GOGOAuthActivity::class.java)) },
                        onGogSignOut         = {
                            CoroutineScope(Dispatchers.Main).launch {
                                GOGService.logout(requireContext())
                                refresh()
                            }
                        },
                        onWifiOnlyChanged    = { PrefManager.downloadOnWifiOnly = it; refresh() },
                        onSharedFolderChanged = { PrefManager.useSingleDownloadFolder = it; refresh() },
                        onDownloadSpeedChanged = { PrefManager.downloadSpeed = it; refresh() },
                        onDownloadServerChanged = { cellId ->
                            PrefManager.cellId = cellId
                            PrefManager.cellIdManuallySet = cellId != 0
                            refresh()
                        },
                        onPickDefaultFolder  = { defaultFolderLauncher.launch(null) },
                        onPickSteamFolder    = { steamFolderLauncher.launch(null) },
                        onPickEpicFolder     = { epicFolderLauncher.launch(null) },
                        onPickGogFolder      = { gogFolderLauncher.launch(null) },
                        onPickAmazonFolder   = { amazonFolderLauncher.launch(null) },
                    )
                }
            }
        }

        // View level ScrollView gives Compose a bounded height required because ComposeView
        // sits inside a weighted LinearLayout that passes unbounded constraints to its children.
        // WARNING: Do NOT add verticalScroll/fillMaxSize to StoresScreen while this is here;
        // fillMaxSize inside an unbounded container crashes at runtime.
        // TODO: Remove this block and use Compose native scrolling once fully migrated off Fragments.
        val density = resources.displayMetrics.density
        val scrollView = ScrollView(requireContext()).apply {
            isFillViewport = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            scrollBarSize = (3 * density).toInt()
            isScrollbarFadingEnabled = true
            scrollBarDefaultDelayBeforeFade = 400
            scrollBarFadeDuration = 250
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setVerticalScrollbarThumbDrawable(GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(android.graphics.Color.argb(100, 26, 159, 255))
                    cornerRadius = 4 * density
                })
            }
            addView(
                composeView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        // Outer FrameLayout with right margin so the scrollbar never touches the screen edge.
        // Background matches the content area so the gap is invisible.
        return FrameLayout(requireContext()).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#0F0F12"))
            addView(
                scrollView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ).apply { marginEnd = (10 * density).toInt() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // Helpers
    private fun refresh() {
        val ctx = context ?: return
        storeState = StoreState(
            isSteamLoggedIn = SteamService.isLoggedIn,
            isEpicLoggedIn  = EpicAuthManager.isLoggedIn(ctx),
            isGogLoggedIn   = GOGAuthManager.isLoggedIn(ctx),
            wifiOnly        = PrefManager.downloadOnWifiOnly,
            sharedFolder    = PrefManager.useSingleDownloadFolder,
            downloadSpeed   = PrefManager.downloadSpeed,
            downloadServer           = PrefManager.cellId,
            downloadServerManuallySet = PrefManager.cellIdManuallySet,
            defaultFolder   = resolveUri(PrefManager.defaultDownloadFolder, ctx),
            steamFolder     = resolveUri(PrefManager.steamDownloadFolder,   ctx),
            epicFolder      = resolveUri(PrefManager.epicDownloadFolder,    ctx),
            gogFolder       = resolveUri(PrefManager.gogDownloadFolder,     ctx),
            amazonFolder    = resolveUri(PrefManager.amazonDownloadFolder,  ctx),
        )
    }

    private fun loadServerOptions(): List<Pair<Int, String>> {
        return try {
            val json = requireContext().assets.open("steam_regions.json")
                .bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            obj.keys().asSequence()
                .mapNotNull { key -> key.toIntOrNull()?.let { id -> id to obj.getString(key) } }
                .sortedBy { it.first }
                .toList()
        } catch (_: Exception) {
            listOf(0 to "Automatic")
        }
    }

    private fun persistUri(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {}
    }

    private fun resolveUri(uriStr: String, ctx: android.content.Context): String {
        if (uriStr.isEmpty()) return ""
        return try {
            FileUtils.getFilePathFromUri(ctx, Uri.parse(uriStr)) ?: uriStr
        } catch (_: Exception) { uriStr }
    }
}
