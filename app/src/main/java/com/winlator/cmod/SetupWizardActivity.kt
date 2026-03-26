package com.winlator.cmod

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contents.AdrenotoolsManager
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.contents.Downloader
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.OnExtractFileListener
import com.winlator.cmod.core.TarCompressorUtils
import com.winlator.cmod.core.TarCompressorUtils.Type
import com.winlator.cmod.core.WineInfo
import com.winlator.cmod.epic.service.EpicAuthManager
import com.winlator.cmod.epic.ui.auth.EpicOAuthActivity
import com.winlator.cmod.gog.service.GOGAuthManager
import com.winlator.cmod.gog.service.GOGService
import com.winlator.cmod.gog.ui.auth.GOGOAuthActivity
import com.winlator.cmod.steam.SteamLoginActivity
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.xenvironment.ImageFs
import com.winlator.cmod.xenvironment.ImageFsInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class SetupWizardActivity : FragmentActivity() {

    companion object {
        private const val PREFS_NAME = "winnative_setup"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_RECOMMENDED_COMPONENTS_DONE = "recommended_components_done"
        private const val KEY_DRIVERS_VISITED = "drivers_visited"
        private const val KEY_DEFAULT_X86_CONTAINER_ID = "default_x86_container_id"
        private const val KEY_DEFAULT_ARM64_CONTAINER_ID = "default_arm64_container_id"
        private const val KEY_DEFAULT_X86_SETTINGS_DONE = "default_x86_settings_done"
        private const val KEY_DEFAULT_ARM64_SETTINGS_DONE = "default_arm64_settings_done"
        private const val KEY_LAST_DRIVER_ID = "last_driver_id"
        private const val KEY_LAST_CONTENT_PREFIX = "last_content_"
        private const val KEY_DEFAULT_JSON_CACHE = "default_json_cache"
        private const val DEFAULT_JSON_URL =
            "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/blob/main/default.json"

        @JvmStatic
        fun isSetupComplete(context: Context): Boolean {
            return prefs(context).getBoolean(KEY_SETUP_COMPLETE, false)
        }

        @JvmStatic
        fun markSetupComplete(context: Context) {
            prefs(context).edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
        }

        @JvmStatic
        fun getPreferredGameContainer(
            context: Context,
            containerManager: ContainerManager
        ): Container? {
            val contentsManager = ContentsManager(context)
            contentsManager.syncContents()
            val preferredId = getDefaultX86ContainerId(context)
            if (preferredId > 0) {
                containerManager.getContainerById(preferredId)?.let {
                    if (isContainerUsable(contentsManager, it)) return it
                }
            }
            return containerManager.containers.firstOrNull { isContainerUsable(contentsManager, it) }
        }

        @JvmStatic
        fun getDefaultX86ContainerId(context: Context): Int {
            return prefs(context).getInt(KEY_DEFAULT_X86_CONTAINER_ID, 0)
        }

        @JvmStatic
        fun getDefaultArm64ContainerId(context: Context): Int {
            return prefs(context).getInt(KEY_DEFAULT_ARM64_CONTAINER_ID, 0)
        }

        @JvmStatic
        fun saveDefaultX86ContainerId(context: Context, containerId: Int) {
            prefs(context).edit().putInt(KEY_DEFAULT_X86_CONTAINER_ID, containerId).apply()
        }

        @JvmStatic
        fun saveDefaultArm64ContainerId(context: Context, containerId: Int) {
            prefs(context).edit().putInt(KEY_DEFAULT_ARM64_CONTAINER_ID, containerId).apply()
        }

        @JvmStatic
        fun recordInstalledDriver(context: Context, driverId: String) {
            prefs(context).edit().putString(KEY_LAST_DRIVER_ID, driverId).apply()
        }

        @JvmStatic
        fun getLastInstalledDriverId(context: Context): String {
            return prefs(context).getString(KEY_LAST_DRIVER_ID, "") ?: ""
        }

        @JvmStatic
        fun recordInstalledContent(context: Context, profile: ContentProfile) {
            val key = KEY_LAST_CONTENT_PREFIX + profile.type.toString().lowercase()
            prefs(context).edit().putString(key, contentVersionIdentifier(profile)).apply()
        }

        @JvmStatic
        fun isWineVersionInstalled(context: Context, wineVersion: String?): Boolean {
            if (wineVersion.isNullOrBlank() || WineInfo.isMainWineVersion(wineVersion)) {
                return true
            }
            val contentsManager = ContentsManager(context)
            contentsManager.syncContents()
            return isWineVersionInstalled(contentsManager, wineVersion)
        }

        @JvmStatic
        fun isContainerUsable(context: Context, container: Container?): Boolean {
            if (container == null) return false
            val contentsManager = ContentsManager(context)
            contentsManager.syncContents()
            return isContainerUsable(contentsManager, container)
        }

        @JvmStatic
        fun promptToInstallWineOrCreateContainer(context: Context, missingWineVersion: String? = null) {
            val runtimeLabel = resolveWineVersionLabel(context, missingWineVersion)
            val message = if (runtimeLabel.isNotBlank()) {
                context.getString(R.string.container_wine_error_not_installed, runtimeLabel)
            } else {
                "Download a Wine/Proton package and create a container before launching games."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            val intent = when {
                !isSetupComplete(context) -> Intent(context, SetupWizardActivity::class.java)
                hasInstalledRuntimes(context) -> Intent(context, MainActivity::class.java)
                    .putExtra("selected_menu_item_id", R.id.main_menu_containers)
                else -> Intent(context, MainActivity::class.java)
                    .putExtra("selected_menu_item_id", R.id.main_menu_contents)
            }
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun isWineVersionInstalled(
            contentsManager: ContentsManager,
            wineVersion: String?
        ): Boolean {
            if (wineVersion.isNullOrBlank() || WineInfo.isMainWineVersion(wineVersion)) {
                return true
            }
            return contentsManager.getProfileByEntryName(wineVersion)?.isInstalled == true
        }

        private fun isContainerUsable(contentsManager: ContentsManager, container: Container): Boolean {
            return isWineVersionInstalled(contentsManager, container.wineVersion)
        }

        private fun hasInstalledRuntimes(context: Context): Boolean {
            val contentsManager = ContentsManager(context)
            contentsManager.syncContents()
            return contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE)
                .orEmpty()
                .any { it.isInstalled } ||
                contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON)
                    .orEmpty()
                    .any { it.isInstalled }
        }

        private fun resolveWineVersionLabel(context: Context, wineVersion: String?): String {
            if (wineVersion.isNullOrBlank()) return ""
            val contentsManager = ContentsManager(context)
            contentsManager.syncContents()
            contentsManager.getProfileByEntryName(wineVersion)?.let { return it.verName }

            val firstDash = wineVersion.indexOf('-')
            val lastDash = wineVersion.lastIndexOf('-')
            return if (firstDash >= 0 && lastDash > firstDash) {
                wineVersion.substring(firstDash + 1, lastDash)
            } else {
                wineVersion
            }
        }

        val provider = GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = R.array.com_google_android_gms_fonts_certs
        )

        val InterFont = FontFamily(
            Font(googleFont = GoogleFont("Inter"), fontProvider = provider)
        )

        val SyncopateFont = FontFamily(
            Font(googleFont = GoogleFont("Syncopate"), fontProvider = provider)
        )
    }

    private data class PackageSpec(
        val label: String,
        val type: ContentProfile.ContentType,
        val url: String,
        val nameHint: String
    )

    private data class RuntimeSpec(
        val label: String,
        val archToken: String,
        val fallbackType: ContentProfile.ContentType,
        val fallbackUrl: String,
        val fallbackNameHint: String,
        val containerDisplayName: (ContentProfile) -> String,
        val persistContainerId: (Context, Int) -> Unit
    )

    private data class RemotePackageSpec(
        val type: ContentProfile.ContentType,
        val verName: String,
        val remoteUrl: String
    )

    private data class TransferState(
        val title: String,
        val detail: String,
        val currentIndex: Int,
        val total: Int,
        val progress: Float? = null
    )

    private data class StoreLoginState(
        val steam: Boolean = false,
        val epic: Boolean = false,
        val gog: Boolean = false
    )

    private val recommendedComponents = listOf(
        PackageSpec(
            label = "DXVK 2.7.1 GPLAsync",
            type = ContentProfile.ContentType.CONTENT_TYPE_DXVK,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Dxvk/Dxvk-2.7.1-gplasync.wcp",
            nameHint = "dxvk-2.7.1-gplasync"
        ),
        PackageSpec(
            label = "DXVK 2.7.1 ARM64EC GPLAsync",
            type = ContentProfile.ContentType.CONTENT_TYPE_DXVK,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Arm64ec-Dxvk/Dxvk-2.7.1-arm64ec-gplasync.wcp",
            nameHint = "Dxvk-2.7.1-arm64ec-gplasync"
        ),
        PackageSpec(
            label = "VKD3D Proton 3.0b",
            type = ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Vk3dk/Vk3dk-proton-3.0b.wcp",
            nameHint = "Vk3dk-proton-3.0b"
        ),
        PackageSpec(
            label = "VKD3D ARM64EC 3.0b",
            type = ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Arm64ec-Vk3dk/Vk3dk-arm64ec-3.0b.wcp",
            nameHint = "Vk3dk-arm64ec-3.0b"
        ),
        PackageSpec(
            label = "FEX 2603",
            type = ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-FEX/FEX-2603.wcp",
            nameHint = "FEX-2603"
        ),
        PackageSpec(
            label = "Box64 0.4.1 fix",
            type = ContentProfile.ContentType.CONTENT_TYPE_BOX64,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Box64/Box64-0.4.1-fix.wcp",
            nameHint = "Box64-0.4.1-fix"
        ),
        PackageSpec(
            label = "Wowbox64 0.4.1",
            type = ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-wowbox64/Wowbox64-0.4.1.wcp",
            nameHint = "Wowbox64-0.4.1"
        )
    )

    private val x86ProtonSpec = RuntimeSpec(
        label = "Recommended x86-64",
        archToken = "x86_64",
        fallbackType = ContentProfile.ContentType.CONTENT_TYPE_WINE,
        fallbackUrl = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Wine/wine-9.20-x86_64.wcp",
        fallbackNameHint = "wine-9.20-x86_64",
        containerDisplayName = { profile ->
            "${runtimeDisplayLabel(profile)} x86-64"
        },
        persistContainerId = ::saveDefaultX86ContainerId
    )

    private val arm64ProtonSpec = RuntimeSpec(
        label = "Recommended ARM64EC",
        archToken = "arm64ec",
        fallbackType = ContentProfile.ContentType.CONTENT_TYPE_PROTON,
        fallbackUrl = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/GameNative/Proton-10-arm64ec-coffincolors.wcp",
        fallbackNameHint = "Proton-10-arm64ec-coffincolors",
        containerDisplayName = { profile ->
            "${runtimeDisplayLabel(profile)} ARM64EC"
        },
        persistContainerId = ::saveDefaultArm64ContainerId
    )

    private val storageGranted = mutableStateOf(false)
    private val notifGranted = mutableStateOf(false)
    private val notifDenied = mutableStateOf(false)

    private val pageIndex = mutableIntStateOf(0)
    private val isAdvancedMode = mutableStateOf(false)
    private val imageFsInstalling = mutableStateOf(false)
    private val imageFsProgress = mutableIntStateOf(0)
    private val imageFsDone = mutableStateOf(false)
    private val recommendedComponentsDone = mutableStateOf(false)
    private val driversVisited = mutableStateOf(false)
    private val x86ProtonDone = mutableStateOf(false)
    private val arm64ProtonDone = mutableStateOf(false)
    private val defaultX86SettingsDone = mutableStateOf(false)
    private val defaultArmSettingsDone = mutableStateOf(false)
    private val defaultX86ContainerName = mutableStateOf("")
    private val defaultArmContainerName = mutableStateOf("")
    private val wizardError = mutableStateOf<String?>(null)
    private val transferState = mutableStateOf<TransferState?>(null)
    private val storeLoginState = mutableStateOf(StoreLoginState())
    private val advancedProfiles = mutableStateListOf<RemotePackageSpec>()
    private val advancedInstalledSet = mutableStateListOf<String>()
    private val advancedContainerNames = mutableStateListOf<String>()

    private var pendingContainerSettingsType: String? = null
    private var recommendedPackageRefreshInFlight = false

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        storageGranted.value = hasStoragePermission()
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted.value = granted
        notifDenied.value = !granted
        if (!granted && Build.VERSION.SDK_INT >= 33 &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            openNotificationSettings()
        }
    }

    private val legacyStoragePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        storageGranted.value =
            permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
    }

    private val containerSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        when (pendingContainerSettingsType) {
            "x86" -> prefs(this).edit().putBoolean(KEY_DEFAULT_X86_SETTINGS_DONE, true).apply()
            "arm64" -> prefs(this).edit().putBoolean(KEY_DEFAULT_ARM64_SETTINGS_DONE, true).apply()
        }
        pendingContainerSettingsType = null
        refreshWizardState()
    }

    private val steamLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStoreState()
    }

    private val gogLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val code = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_AUTH_CODE)
            if (!code.isNullOrBlank()) {
                lifecycleScope.launch {
                    val authResult = GOGAuthManager.authenticateWithCode(this@SetupWizardActivity, code)
                    if (authResult.isSuccess) {
                        GOGService.start(this@SetupWizardActivity)
                    }
                    refreshStoreState()
                }
            } else {
                refreshStoreState()
            }
        } else {
            refreshStoreState()
        }
    }

    private val epicLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val code = result.data?.getStringExtra(EpicOAuthActivity.EXTRA_AUTH_CODE)
            lifecycleScope.launch {
                if (!code.isNullOrBlank()) {
                    EpicAuthManager.authenticateWithCode(this@SetupWizardActivity, code)
                }
                refreshStoreState()
            }
        } else {
            refreshStoreState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.setFragmentResultListener(
            SetupWizardDriversDialogFragment.RESULT_KEY,
            this
        ) { _, _ ->
            prefs(this).edit().putBoolean(KEY_DRIVERS_VISITED, true).apply()
            refreshWizardState()
        }

        if (isSetupComplete(this) && ImageFs.find(this).isValid) {
            launchApp()
            return
        }

        storageGranted.value = hasStoragePermission()
        notifGranted.value = hasNotificationPermissionSilently()
        refreshWizardState()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF57CBDE),
                    secondary = Color(0xFF3FB950),
                    background = Color(0xFF0D1117),
                    surface = Color(0xFF161B22)
                )
            ) {
                SetupWizardScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        storageGranted.value = hasStoragePermission()
        val notificationsEnabled = hasNotificationPermissionSilently()
        notifGranted.value = notificationsEnabled
        if (notificationsEnabled) notifDenied.value = false
        refreshWizardState()
        refreshRecommendedPackageCache()
    }

    private fun refreshWizardState() {
        val imageFs = ImageFs.find(this)
        imageFsDone.value = imageFs.isValid && imageFs.version >= ImageFsInstaller.LATEST_VERSION.toInt()

        val preferences = prefs(this)
        val contentsManager = ContentsManager(this)
        contentsManager.syncContents()

        val referenceRecommendedComponents = getCachedRecommendedComponentSpecs().ifEmpty { recommendedComponents }
        recommendedComponentsDone.value =
            preferences.getBoolean(KEY_RECOMMENDED_COMPONENTS_DONE, false) ||
                referenceRecommendedComponents.all { isPackageInstalled(contentsManager, it) }
        if (recommendedComponentsDone.value) {
            preferences.edit().putBoolean(KEY_RECOMMENDED_COMPONENTS_DONE, true).apply()
        }

        driversVisited.value = preferences.getBoolean(KEY_DRIVERS_VISITED, false)

        val containerManager = ContainerManager(this)
        val x86Container = containerManager.getContainerById(getDefaultX86ContainerId(this))
            ?.takeIf { isContainerUsable(this, it) }
        val armContainer = containerManager.getContainerById(getDefaultArm64ContainerId(this))
            ?.takeIf { isContainerUsable(this, it) }

        x86ProtonDone.value = x86Container != null
        arm64ProtonDone.value = armContainer != null
        defaultX86ContainerName.value = x86Container?.name ?: ""
        defaultArmContainerName.value = armContainer?.name ?: ""

        defaultX86SettingsDone.value =
            preferences.getBoolean(KEY_DEFAULT_X86_SETTINGS_DONE, false) && x86Container != null
        defaultArmSettingsDone.value =
            preferences.getBoolean(KEY_DEFAULT_ARM64_SETTINGS_DONE, false) && armContainer != null

        refreshStoreState()
    }

    private fun refreshStoreState() {
        storeLoginState.value = StoreLoginState(
            steam = SteamService.isLoggedIn,
            epic = EpicAuthManager.isLoggedIn(this),
            gog = GOGAuthManager.isLoggedIn(this)
        )
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasNotificationPermissionSilently(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun requestFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            }
        } else {
            val preferences = prefs(this)
            val hasRequestedOnce = preferences.getBoolean("storage_requested_once", false)
            val shouldShowRationale =
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)

            if (hasRequestedOnce && !shouldShowRationale) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            } else {
                preferences.edit().putBoolean("storage_requested_once", true).apply()
                legacyStoragePermLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && applicationInfo.targetSdkVersion >= 33) {
            if (notifDenied.value) {
                openNotificationSettings()
            } else {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun installImageFs() {
        if (imageFsInstalling.value || imageFsDone.value) return

        wizardError.value = null
        imageFsInstalling.value = true
        imageFsProgress.intValue = 0
        val imageFs = ImageFs.find(this)
        val rootDir = imageFs.rootDir

        Executors.newSingleThreadExecutor().execute {
            try {
                clearRootDir(rootDir)

                val compressionRatio = 22
                var contentLength = 0L
                val assetSize = FileUtils.getSize(this, "imagefs.txz")
                contentLength += if (assetSize > 0) {
                    (assetSize * (100.0f / compressionRatio)).toLong()
                } else {
                    800_000_000L
                }

                try {
                    val versions = resources.getStringArray(R.array.wine_entries)
                    versions.forEach { version ->
                        val versionSize = FileUtils.getSize(this, "$version.txz")
                        contentLength += if (versionSize > 0) {
                            (versionSize * (100.0f / compressionRatio)).toLong()
                        } else {
                            100_000_000L
                        }
                    }
                } catch (_: Exception) {
                }

                val totalSize = AtomicLong()
                val listener = OnExtractFileListener { file, size ->
                    if (size > 0) {
                        val total = totalSize.addAndGet(size)
                        val percent = ((total.toFloat() / contentLength) * 100f).toInt().coerceIn(0, 100)
                        runOnUiThread { imageFsProgress.intValue = percent }
                    }
                    file
                }

                val success = TarCompressorUtils.extract(
                    Type.XZ,
                    this,
                    "imagefs.txz",
                    rootDir,
                    listener
                )

                if (!success) {
                    runOnUiThread {
                        imageFsInstalling.value = false
                        wizardError.value = "ImageFS extraction failed. Check available storage and try again."
                    }
                    return@execute
                }

                try {
                    resources.getStringArray(R.array.wine_entries).forEach { version ->
                        val outFile = File(rootDir, "/opt/$version")
                        outFile.mkdirs()
                        TarCompressorUtils.extract(Type.XZ, this, "$version.txz", outFile, listener)
                    }
                } catch (_: Exception) {
                }

                try {
                    val manager = AdrenotoolsManager(this)
                    resources.getStringArray(R.array.wrapper_graphics_driver_version_entries).forEach { driver ->
                        manager.extractDriverFromResources(driver)
                    }
                } catch (_: Exception) {
                }

                imageFs.createImgVersionFile(ImageFsInstaller.LATEST_VERSION.toInt())
                runOnUiThread {
                    imageFsProgress.intValue = 100
                    imageFsInstalling.value = false
                    imageFsDone.value = true
                    refreshWizardState()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    imageFsInstalling.value = false
                    wizardError.value = "ImageFS install failed: ${e.message}"
                }
            }
        }
    }

    private fun installRecommendedComponents() {
        if (transferState.value != null || recommendedComponentsDone.value) return

        lifecycleScope.launch {
            wizardError.value = null
            val success = withContext(Dispatchers.IO) {
                try {
                    val specs = resolveRecommendedComponentSpecs()
                    specs.forEachIndexed { index, spec ->
                        val profile = downloadAndInstallPackage(spec, index, specs.size)
                        if (profile == null) return@withContext false
                    }
                    prefs(this@SetupWizardActivity).edit()
                        .putBoolean(KEY_RECOMMENDED_COMPONENTS_DONE, true)
                        .apply()
                    true
                } catch (e: Exception) {
                    wizardError.value = "Component install failed: ${e.message}"
                    false
                } finally {
                    transferState.value = null
                }
            }
            if (success) refreshWizardState()
        }
    }

    private fun installRecommendedProton(spec: RuntimeSpec) {
        if (transferState.value != null) return

        lifecycleScope.launch {
            wizardError.value = null
            val created = withContext(Dispatchers.IO) {
                try {
                    val resolvedSpec = resolveRecommendedRuntimeSpec(spec)
                    transferState.value = TransferState(
                        title = spec.label,
                        detail = getString(R.string.downloads_queue_preparing_download),
                        currentIndex = 0,
                        total = 2
                    )

                    val downloaded = downloadFileToCache(
                        label = spec.label,
                        url = resolvedSpec.url,
                        currentIndex = 1,
                        total = 2
                    )
                    if (downloaded == null) return@withContext null

                    transferState.value = TransferState(
                        title = spec.label,
                        detail = getString(R.string.downloads_queue_installing_package),
                        currentIndex = 2,
                        total = 2,
                        progress = null
                    )

                    val profile = installDownloadedPackage(downloaded, resolvedSpec.url)
                    downloaded.delete()
                    if (profile == null) return@withContext null

                    val container = ensureContainerForProfile(profile, spec.containerDisplayName(profile))
                    spec.persistContainerId(this@SetupWizardActivity, container.id)
                    container
                } catch (e: Exception) {
                    wizardError.value = "${spec.label} failed: ${e.message}"
                    null
                } finally {
                    transferState.value = null
                }
            }
            if (created != null) refreshWizardState()
        }
    }

    private suspend fun downloadAndInstallPackage(
        spec: PackageSpec,
        index: Int,
        total: Int
    ): ContentProfile? {
        transferState.value = TransferState(
            title = getString(R.string.setup_wizard_recommended_components),
            detail = "Downloading ${spec.label}",
            currentIndex = index + 1,
            total = total,
            progress = 0f
        )

        val downloaded = downloadFileToCache(
            label = spec.label,
            url = spec.url,
            currentIndex = index + 1,
            total = total
        ) ?: return null

        transferState.value = TransferState(
            title = getString(R.string.setup_wizard_recommended_components),
            detail = "Installing ${spec.label}",
            currentIndex = index + 1,
            total = total,
            progress = null
        )

        val profile = installDownloadedPackage(downloaded, spec.url)
        downloaded.delete()
        return profile
    }

    private suspend fun downloadFileToCache(
        label: String,
        url: String,
        currentIndex: Int,
        total: Int
    ): File? = withContext(Dispatchers.IO) {
        val sanitized = label.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        val output = File(cacheDir, "wizard_${System.currentTimeMillis()}_$sanitized.wcp")
        val listener = Downloader.DownloadListener { downloadedBytes, totalBytes ->
            transferState.value = TransferState(
                title = transferState.value?.title ?: label,
                detail = "Downloading $label",
                currentIndex = currentIndex,
                total = total,
                progress = if (totalBytes > 0) {
                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    null
                }
            )
        }
        val success = Downloader.downloadFileWinNativeFirst(url, output, listener)
        if (success) output else null
    }

    private fun installDownloadedPackage(file: File, sourceUrl: String): ContentProfile? {
        val manager = ContentsManager(this)
        manager.syncContents()

        var extractedProfile: ContentProfile? = null
        var installedProfile: ContentProfile? = null
        var failed = false

        val callback = object : ContentsManager.OnInstallFinishedCallback {
            private var extracting = true

            override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                if (reason == ContentsManager.InstallFailedReason.ERROR_EXIST && extractedProfile != null) {
                    manager.registerRemoteProfileAlias(sourceUrl, extractedProfile)
                    manager.syncContents()
                    installedProfile = manager.getProfileByEntryName(
                        ContentsManager.getEntryName(extractedProfile)
                    ) ?: extractedProfile?.apply { isInstalled = true }
                    return
                }
                failed = true
            }

            override fun onSucceed(profile: ContentProfile) {
                if (extracting) {
                    extracting = false
                    extractedProfile = profile
                    manager.finishInstallContent(profile, this)
                    return
                }
                manager.registerRemoteProfileAlias(sourceUrl, profile)
                manager.syncContents()
                recordInstalledContent(this@SetupWizardActivity, profile)
                installedProfile = profile
            }
        }

        manager.extraContentFile(Uri.fromFile(file), callback)
        return if (failed) null else installedProfile
    }

    private fun resolveRecommendedComponentSpecs(): List<PackageSpec> {
        val componentTypes = setOf(
            ContentProfile.ContentType.CONTENT_TYPE_DXVK,
            ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
            ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
            ContentProfile.ContentType.CONTENT_TYPE_BOX64,
            ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
        )
        val remoteSpecs = fetchRecommendedPackages()
            .filter { it.type in componentTypes }
            .map {
                PackageSpec(
                    label = it.verName,
                    type = it.type,
                    url = it.remoteUrl,
                    nameHint = it.verName
                )
            }
        return remoteSpecs.ifEmpty { recommendedComponents }
    }

    private fun resolveRecommendedRuntimeSpec(spec: RuntimeSpec): PackageSpec {
        val resolved = fetchRecommendedPackages().firstOrNull {
            (it.type == ContentProfile.ContentType.CONTENT_TYPE_WINE ||
                it.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON) &&
                it.verName.contains(spec.archToken, ignoreCase = true)
        }

        if (resolved != null) {
            return PackageSpec(
                label = spec.label,
                type = resolved.type,
                url = resolved.remoteUrl,
                nameHint = resolved.verName
            )
        }

        return PackageSpec(
            label = spec.label,
            type = spec.fallbackType,
            url = spec.fallbackUrl,
            nameHint = spec.fallbackNameHint
        )
    }

    private fun fetchRecommendedPackages(): List<RemotePackageSpec> {
        val json = Downloader.downloadString(resolveJsonDownloadUrl(DEFAULT_JSON_URL))
        if (!json.isNullOrBlank()) {
            prefs(this).edit().putString(KEY_DEFAULT_JSON_CACHE, json).apply()
            return parseRecommendedPackages(json)
        }
        return getCachedRecommendedPackages()
    }

    private fun refreshRecommendedPackageCache() {
        if (recommendedPackageRefreshInFlight) return

        recommendedPackageRefreshInFlight = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    fetchRecommendedPackages()
                }
            } finally {
                recommendedPackageRefreshInFlight = false
                refreshWizardState()
            }
        }
    }

    private fun getCachedRecommendedPackages(): List<RemotePackageSpec> {
        val cachedJson = prefs(this).getString(KEY_DEFAULT_JSON_CACHE, null)
        return parseRecommendedPackages(cachedJson)
    }

    private fun getCachedRecommendedComponentSpecs(): List<PackageSpec> {
        return getCachedRecommendedPackages()
            .filter {
                it.type != ContentProfile.ContentType.CONTENT_TYPE_WINE &&
                    it.type != ContentProfile.ContentType.CONTENT_TYPE_PROTON
            }
            .map {
                PackageSpec(
                    label = it.verName,
                    type = it.type,
                    url = it.remoteUrl,
                    nameHint = it.verName
                )
            }
    }

    private fun parseRecommendedPackages(json: String?): List<RemotePackageSpec> {
        if (json.isNullOrBlank()) return emptyList()

        return runCatching {
            val entries = JSONArray(json)
            buildList {
                for (index in 0 until entries.length()) {
                    val item = entries.optJSONObject(index) ?: continue
                    val type = ContentProfile.ContentType.getTypeByName(item.optString("type")) ?: continue
                    val verName = item.optString("verName")
                    val remoteUrl = item.optString("remoteUrl")
                    if (verName.isBlank() || remoteUrl.isBlank()) continue
                    add(RemotePackageSpec(type, verName, remoteUrl))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun ensureContainerForProfile(profile: ContentProfile, desiredName: String): Container {
        val containerManager = ContainerManager(this)
        containerManager.containers.firstOrNull { it.name == desiredName }?.let {
            val resolvedWineVersion = ContentsManager.getEntryName(profile)
            if (it.wineVersion != resolvedWineVersion) {
                it.setWineVersion(resolvedWineVersion)
                it.putExtra("wineprefixNeedsUpdate", "t")
                it.saveData()
            }
            applyRecommendedContainerDefaults(it)
            return it
        }

        val contentsManager = ContentsManager(this)
        contentsManager.syncContents()
        val data = JSONObject().apply {
            put("name", desiredName)
            put("wineVersion", ContentsManager.getEntryName(profile))
        }

        return requireNotNull(containerManager.createContainer(data, contentsManager)) {
            "Unable to create container for ${profile.verName}"
        }.also {
            applyRecommendedContainerDefaults(it)
        }
    }

    private fun applyRecommendedContainerDefaults(container: Container) {
        val contentsManager = ContentsManager(this)
        contentsManager.syncContents()
        val wineInfo = WineInfo.fromIdentifier(this, contentsManager, container.wineVersion)
        val isArm64 = wineInfo.isArm64EC

        container.setGraphicsDriver(Container.DEFAULT_GRAPHICS_DRIVER)
        container.setGraphicsDriverConfig(
            replaceDelimitedConfigValue(
                Container.DEFAULT_GRAPHICSDRIVERCONFIG,
                ';',
                "version",
                resolvePreferredDriverVersion()
            )
        )
        container.setDXWrapper(Container.DEFAULT_DXWRAPPER)
        container.setDXWrapperConfig(
            replaceDelimitedConfigValue(
                replaceDelimitedConfigValue(
                    Container.DEFAULT_DXWRAPPERCONFIG,
                    ',',
                    "version",
                    resolvePreferredContentVersion(
                        contentsManager,
                        ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                        DefaultVersion.DXVK,
                        if (isArm64) Regex("arm64ec", RegexOption.IGNORE_CASE) else null,
                        if (isArm64) null else Regex("arm64ec", RegexOption.IGNORE_CASE)
                    )
                ),
                ',',
                "vkd3dVersion",
                resolvePreferredContentVersion(
                    contentsManager,
                    ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                    DefaultVersion.VKD3D,
                    if (isArm64) Regex("arm64ec", RegexOption.IGNORE_CASE) else null,
                    if (isArm64) null else Regex("arm64ec", RegexOption.IGNORE_CASE)
                )
            )
        )

        if (isArm64) {
            container.setEmulator("fexcore")
            container.setEmulator64("fexcore")
            container.setBox64Version(
                resolvePreferredContentVersion(
                    contentsManager,
                    ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
                    DefaultVersion.WOWBOX64
                )
            )
            container.setFEXCoreVersion(
                resolvePreferredContentVersion(
                    contentsManager,
                    ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
                    DefaultVersion.FEXCORE
                )
            )
        } else {
            container.setEmulator("box64")
            container.setEmulator64("box64")
            container.setBox64Version(
                resolvePreferredContentVersion(
                    contentsManager,
                    ContentProfile.ContentType.CONTENT_TYPE_BOX64,
                    DefaultVersion.BOX64
                )
            )
            container.setFEXCoreVersion(
                resolvePreferredContentVersion(
                    contentsManager,
                    ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
                    DefaultVersion.FEXCORE
                )
            )
        }

        container.saveData()
    }

    private fun resolvePreferredDriverVersion(): String {
        val adrenotoolsManager = AdrenotoolsManager(this)
        val installedDrivers = adrenotoolsManager.enumarateInstalledDrivers()
        val preferredDriver = getLastInstalledDriverId(this)
        if (preferredDriver.isNotBlank() && installedDrivers.contains(preferredDriver)) {
            return preferredDriver
        }
        return try {
            if (com.winlator.cmod.core.GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, this)) {
                DefaultVersion.WRAPPER_ADRENO
            } else {
                DefaultVersion.WRAPPER
            }
        } catch (_: Throwable) {
            DefaultVersion.WRAPPER
        }
    }

    private fun resolvePreferredContentVersion(
        manager: ContentsManager,
        type: ContentProfile.ContentType,
        fallback: String,
        includePattern: Regex? = null,
        excludePattern: Regex? = null
    ): String {
        val preferenceKey = "last_content_${type.toString().lowercase()}"
        val preferred = prefs(this).getString(preferenceKey, "") ?: ""
        val installedProfiles = manager.getProfiles(type).orEmpty().filter { it.isInstalled }
        val matchingProfiles = installedProfiles.filter { profile ->
            val versionName = profile.verName
            (includePattern == null || includePattern.containsMatchIn(versionName)) &&
                (excludePattern == null || !excludePattern.containsMatchIn(versionName))
        }.ifEmpty { installedProfiles }

        if (preferred.isNotBlank() && matchingProfiles.any { contentVersionIdentifier(it) == preferred }) {
            return preferred
        }

        val newestInstalled = matchingProfiles.maxWithOrNull(
            compareBy<ContentProfile> { it.verCode }.thenBy { it.verName.lowercase() }
        )
        return newestInstalled?.let(::contentVersionIdentifier) ?: fallback
    }

    private fun replaceDelimitedConfigValue(
        config: String,
        delimiter: Char,
        key: String,
        value: String
    ): String {
        val parts = config.split(delimiter).toMutableList()
        var replaced = false
        for (index in parts.indices) {
            if (parts[index].startsWith("$key=")) {
                parts[index] = "$key=$value"
                replaced = true
            }
        }
        if (!replaced) {
            parts += "$key=$value"
        }
        return parts.joinToString(delimiter.toString())
    }

    private fun isPackageInstalled(manager: ContentsManager, spec: PackageSpec): Boolean {
        return manager.getProfiles(spec.type).orEmpty().any { profile ->
            profile.isInstalled && profile.verName.contains(spec.nameHint, ignoreCase = true)
        }
    }

    private fun openDrivers() {
        if (supportFragmentManager.findFragmentByTag(SetupWizardDriversDialogFragment.TAG) == null) {
            SetupWizardDriversDialogFragment().show(
                supportFragmentManager,
                SetupWizardDriversDialogFragment.TAG
            )
        }
    }

    private fun enterAdvancedMode() {
        isAdvancedMode.value = true
        pageIndex.intValue = 1
        loadAdvancedProfiles()
    }

    private fun loadAdvancedProfiles() {
        if (advancedProfiles.isNotEmpty()) return
        lifecycleScope.launch {
            val profiles = withContext(Dispatchers.IO) {
                Downloader.clearFileMap()
                fetchRecommendedPackages()
            }
            advancedProfiles.clear()
            advancedProfiles.addAll(profiles)
            refreshAdvancedInstalledSet()
        }
    }

    private fun refreshAdvancedInstalledSet() {
        val manager = ContentsManager(this)
        manager.syncContents()
        advancedInstalledSet.clear()
        advancedProfiles.forEach { spec ->
            val installed = manager.getProfiles(spec.type).orEmpty().any {
                it.isInstalled && it.verName.equals(spec.verName, ignoreCase = true)
            }
            if (installed) advancedInstalledSet.add(spec.verName)
        }
        // Also refresh container names for default settings page
        val containerManager = ContainerManager(this)
        advancedContainerNames.clear()
        containerManager.containers.forEach {
            advancedContainerNames.add(it.name)
        }
    }

    private fun installAdvancedComponent(spec: RemotePackageSpec) {
        if (transferState.value != null) return
        lifecycleScope.launch {
            wizardError.value = null
            val profile = withContext(Dispatchers.IO) {
                try {
                    transferState.value = TransferState(
                        title = spec.verName,
                        detail = getString(R.string.downloads_queue_preparing_download),
                        currentIndex = 1,
                        total = 1
                    )
                    val downloaded = downloadFileToCache(
                        label = spec.verName,
                        url = spec.remoteUrl,
                        currentIndex = 1,
                        total = 1
                    )
                    if (downloaded == null) return@withContext null

                    transferState.value = TransferState(
                        title = spec.verName,
                        detail = getString(R.string.setup_wizard_installing),
                        currentIndex = 1,
                        total = 1,
                        progress = null
                    )

                    val installed = installDownloadedPackage(downloaded, spec.remoteUrl)
                    downloaded.delete()
                    installed
                } catch (e: Exception) {
                    wizardError.value = "Install failed: ${e.message}"
                    null
                } finally {
                    transferState.value = null
                }
            }
            if (profile != null) {
                // Auto-create container for Wine/Proton
                if (spec.type == ContentProfile.ContentType.CONTENT_TYPE_WINE ||
                    spec.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON) {
                    withContext(Dispatchers.IO) {
                        try {
                            val displayName = runtimeDisplayLabel(profile)
                            val container = ensureContainerForProfile(profile, displayName)
                            // Persist container IDs for default settings page
                            if (profile.verName.contains("arm64ec", ignoreCase = true)) {
                                saveDefaultArm64ContainerId(this@SetupWizardActivity, container.id)
                            } else {
                                saveDefaultX86ContainerId(this@SetupWizardActivity, container.id)
                            }
                        } catch (e: Exception) {
                            wizardError.value = "Container creation failed: ${e.message}"
                        }
                    }
                }
                refreshAdvancedInstalledSet()
                refreshWizardState()
            }
        }
    }

    private fun openContainerDefaultSettings(containerId: Int, type: String) {
        pendingContainerSettingsType = type
        containerSettingsLauncher.launch(
            Intent(this, MainActivity::class.java)
                .putExtra("edit_container_id", containerId)
        )
    }

    private fun finishWizard() {
        markSetupComplete(this)
        launchApp()
    }

    private fun clearRootDir(rootDir: File) {
        if (rootDir.isDirectory) {
            rootDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name == "home") return@forEach
                FileUtils.delete(file)
            }
        } else {
            rootDir.mkdirs()
        }
    }

    private fun launchApp() {
        startActivity(Intent(this, UnifiedActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    @Composable
    private fun SetupWizardScreen() {
        val page by pageIndex
        val advanced = isAdvancedMode.value
        val scrollState = rememberScrollState()
        val totalPages = if (advanced) 4 else 5
        val pageTitle = when {
            page == 0 -> stringResource(R.string.setup_wizard_required_access)
            advanced && page == 1 -> stringResource(R.string.setup_wizard_select_components)
            advanced && page == 2 -> stringResource(R.string.setup_wizard_default_settings)
            advanced && page == 3 -> stringResource(R.string.stores_accounts_title)
            !advanced && page == 1 -> stringResource(R.string.setup_wizard_recommended_components)
            !advanced && page == 2 -> stringResource(R.string.setup_wizard_recommended_wine_proton)
            !advanced && page == 3 -> stringResource(R.string.setup_wizard_default_settings)
            else -> stringResource(R.string.stores_accounts_title)
        }
        val canGoNext = when {
            page == 0 -> storageGranted.value && imageFsDone.value
            page == 1 && !advanced -> recommendedComponentsDone.value && driversVisited.value
            page == 1 && advanced -> true // Advanced components: always allow Next
            page == 2 && !advanced -> x86ProtonDone.value && arm64ProtonDone.value
            page == 2 && advanced -> true // Default settings in advanced: optional
            page == 3 && !advanced -> defaultX86SettingsDone.value && defaultArmSettingsDone.value
            else -> true
        }
        val lastPage = totalPages - 1

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = stringResource(if (advanced) R.string.setup_wizard_advanced_setup else R.string.setup_wizard_title),
                            color = Color(0xFFE6EDF3),
                            fontFamily = SyncopateFont,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = stringResource(R.string.setup_wizard_step_format, page + 1, totalPages),
                        color = Color(0xFF8B949E),
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = pageTitle,
                            color = Color(0xFFE6EDF3),
                            fontFamily = InterFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .widthIn(max = 720.dp)
                ) {
                    if (advanced) {
                        when (page) {
                            0 -> PagePermissions()
                            1 -> PageAdvancedComponents()
                            2 -> PageDefaultSettings()
                            3 -> PageStores()
                        }
                    } else {
                        when (page) {
                            0 -> PagePermissions()
                            1 -> PageComponents()
                            2 -> PageWineAndProton()
                            3 -> PageDefaultSettings()
                            4 -> PageStores()
                        }
                    }

                    wizardError.value?.let { message ->
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = message,
                            color = Color(0xFFFF7B72),
                            fontFamily = InterFont,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (page == 1 && !advanced) {
                        OutlinedButton(
                            onClick = { enterAdvancedMode() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE6EDF3)
                            )
                        ) {
                            Text(stringResource(R.string.setup_wizard_advanced_user), fontFamily = InterFont)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (page > 0) pageIndex.intValue -= 1
                                else if (advanced) {
                                    isAdvancedMode.value = false
                                    pageIndex.intValue = 1
                                }
                            },
                            enabled = page > 0 || advanced,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE6EDF3),
                                disabledContentColor = Color(0xFF6E7681)
                            )
                        ) {
                            Text(stringResource(R.string.common_ui_back), fontFamily = InterFont)
                        }
                    }

                    if (page < lastPage) {
                        Button(
                            onClick = { if (canGoNext) pageIndex.intValue += 1 },
                            enabled = canGoNext,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF238636),
                                disabledContainerColor = Color(0xFF30363D),
                                disabledContentColor = Color(0xFF8B949E)
                            )
                        ) {
                            Text(stringResource(R.string.setup_wizard_next), fontFamily = InterFont, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { finishWizard() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                        ) {
                            Text(stringResource(R.string.setup_wizard_finish), fontFamily = InterFont, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            transferState.value?.let { transfer ->
                TransferDialog(transfer)
            }
        }
    }

    @Composable
    private fun PagePermissions() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                WizardActionCard(
                    title = stringResource(R.string.setup_wizard_allow_file_access),
                    subtitle = stringResource(R.string.common_ui_required),
                    completed = storageGranted.value,
                    buttonLabel = stringResource(if (storageGranted.value) R.string.setup_wizard_granted else R.string.setup_wizard_grant),
                    onClick = { requestFileAccess() }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                WizardActionCard(
                    title = stringResource(R.string.common_ui_notifications),
                    subtitle = stringResource(R.string.common_ui_optional),
                    completed = notifGranted.value,
                    buttonLabel = when {
                        notifGranted.value -> stringResource(R.string.setup_wizard_granted)
                        notifDenied.value -> stringResource(R.string.setup_wizard_denied)
                        else -> stringResource(R.string.setup_wizard_allow)
                    },
                    onClick = { requestNotifications() }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        WizardActionCard(
            title = stringResource(R.string.setup_wizard_install_system_files),
            subtitle = stringResource(R.string.common_ui_required),
            completed = imageFsDone.value,
            buttonLabel = when {
                imageFsDone.value -> stringResource(R.string.common_ui_installed)
                imageFsInstalling.value -> stringResource(R.string.setup_wizard_installing)
                else -> stringResource(R.string.setup_wizard_install_system_files)
            },
            onClick = { installImageFs() },
            enabled = !imageFsInstalling.value
        )

        if (imageFsInstalling.value) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { imageFsProgress.intValue / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF57CBDE),
                trackColor = Color(0xFF21262D)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${imageFsProgress.intValue}%",
                color = Color(0xFF57CBDE),
                fontFamily = SyncopateFont,
                fontSize = 12.sp
            )
        }
    }

    @Composable
    private fun PageComponents() {
        Text(
            text = stringResource(R.string.setup_wizard_recommended_components_description),
            color = Color(0xFF8B949E),
            fontFamily = InterFont,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        WizardActionCard(
            title = stringResource(R.string.setup_wizard_recommended_components),
            subtitle = stringResource(R.string.common_ui_required),
            completed = recommendedComponentsDone.value,
            buttonLabel = stringResource(if (recommendedComponentsDone.value) R.string.common_ui_installed else R.string.setup_wizard_download_and_install),
            onClick = { installRecommendedComponents() },
            enabled = transferState.value == null
        )
        Spacer(Modifier.height(12.dp))
        WizardActionCard(
            title = stringResource(R.string.settings_drivers_title),
            subtitle = stringResource(R.string.common_ui_required),
            completed = driversVisited.value,
            buttonLabel = stringResource(if (driversVisited.value) R.string.common_ui_done else R.string.setup_wizard_open_drivers),
            onClick = { openDrivers() },
            enabled = imageFsDone.value
        )
    }

    @Composable
    private fun PageWineAndProton() {
        Text(
            text = stringResource(R.string.setup_wizard_each_button_downloads_description),
            color = Color(0xFF8B949E),
            fontFamily = InterFont,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        WizardActionCard(
            title = stringResource(R.string.setup_wizard_recommended_x86_64),
            subtitle = stringResource(R.string.common_ui_required),
            completed = x86ProtonDone.value,
            buttonLabel = stringResource(if (x86ProtonDone.value) R.string.setup_wizard_ready else R.string.setup_wizard_download_and_create),
            onClick = { installRecommendedProton(x86ProtonSpec) },
            enabled = transferState.value == null
        )
        Spacer(Modifier.height(12.dp))
        WizardActionCard(
            title = stringResource(R.string.setup_wizard_recommended_arm64ec),
            subtitle = stringResource(R.string.common_ui_required),
            completed = arm64ProtonDone.value,
            buttonLabel = stringResource(if (arm64ProtonDone.value) R.string.setup_wizard_ready else R.string.setup_wizard_download_and_create),
            onClick = { installRecommendedProton(arm64ProtonSpec) },
            enabled = transferState.value == null
        )
    }

    @Composable
    private fun PageAdvancedComponents() {
        val typeOrder = listOf(
            ContentProfile.ContentType.CONTENT_TYPE_WINE,
            ContentProfile.ContentType.CONTENT_TYPE_PROTON,
            ContentProfile.ContentType.CONTENT_TYPE_DXVK,
            ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
            ContentProfile.ContentType.CONTENT_TYPE_BOX64,
            ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
            ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
        )
        val typeLabels = mapOf(
            ContentProfile.ContentType.CONTENT_TYPE_WINE to "Wine",
            ContentProfile.ContentType.CONTENT_TYPE_PROTON to "Proton",
            ContentProfile.ContentType.CONTENT_TYPE_DXVK to "DXVK",
            ContentProfile.ContentType.CONTENT_TYPE_VKD3D to "VKD3D",
            ContentProfile.ContentType.CONTENT_TYPE_BOX64 to "Box64",
            ContentProfile.ContentType.CONTENT_TYPE_FEXCORE to "FEXCore",
            ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64 to "Wowbox64"
        )
        var selectedTab by remember { mutableStateOf(typeOrder[0]) }

        Text(
            text = stringResource(R.string.setup_wizard_choose_components_description),
            color = Color(0xFF8B949E),
            fontFamily = InterFont,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))

        // Also show Drivers button
        WizardActionCard(
            title = stringResource(R.string.settings_drivers_title),
            subtitle = stringResource(R.string.common_ui_required),
            completed = driversVisited.value,
            buttonLabel = stringResource(if (driversVisited.value) R.string.common_ui_done else R.string.setup_wizard_open_drivers),
            onClick = { openDrivers() },
            enabled = imageFsDone.value
        )
        Spacer(Modifier.height(14.dp))

        // Tab row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            typeOrder.forEach { type ->
                val isSelected = type == selectedTab
                val hasInstalled = advancedProfiles.any {
                    it.type == type && it.verName in advancedInstalledSet
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = type },
                    color = when {
                        isSelected -> Color(0xFF238636)
                        hasInstalled -> Color(0xFF1F6F43)
                        else -> Color(0xFF21262D)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = typeLabels[type] ?: type.toString(),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        color = Color(0xFFE6EDF3),
                        fontFamily = InterFont,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Component list for selected tab
        val tabProfiles = advancedProfiles.filter { it.type == selectedTab }

        if (advancedProfiles.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF57CBDE),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.setup_wizard_loading_components),
                    color = Color(0xFF8B949E),
                    fontFamily = InterFont,
                    fontSize = 13.sp
                )
            }
        } else if (tabProfiles.isEmpty()) {
            Text(
                text = stringResource(R.string.setup_wizard_no_components_available),
                color = Color(0xFF8B949E),
                fontFamily = InterFont,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            tabProfiles.forEach { spec ->
                val installed = spec.verName in advancedInstalledSet
                AdvancedComponentCard(
                    name = spec.verName,
                    installed = installed,
                    onClick = { installAdvancedComponent(spec) },
                    enabled = transferState.value == null && !installed
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    @Composable
    private fun AdvancedComponentCard(
        name: String,
        installed: Boolean,
        onClick: () -> Unit,
        enabled: Boolean = true
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF161B22),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = Color(0xFFE6EDF3),
                        fontFamily = InterFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
                Button(
                    onClick = onClick,
                    enabled = enabled && !installed,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (installed) Color(0xFF1F6F43) else Color(0xFF57CBDE),
                        contentColor = if (installed) Color.White else Color.Black,
                        disabledContainerColor = if (installed) Color(0xFF1F6F43) else Color(0xFF30363D),
                        disabledContentColor = if (installed) Color.White else Color(0xFF8B949E)
                    )
                ) {
                    Text(
                        text = stringResource(if (installed) R.string.common_ui_installed else R.string.common_ui_install),
                        fontFamily = InterFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun PageDefaultSettings() {
        Text(
            text = stringResource(R.string.setup_wizard_containers_description),
            color = Color(0xFF8B949E),
            fontFamily = InterFont,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        val x86Id = getDefaultX86ContainerId(this)
        val armId = getDefaultArm64ContainerId(this)

        WizardActionCard(
            title = defaultX86ContainerName.value.ifBlank { "x86-64 container" },
            subtitle = stringResource(R.string.setup_wizard_default_settings),
            completed = defaultX86SettingsDone.value,
            buttonLabel = stringResource(if (defaultX86SettingsDone.value) R.string.setup_wizard_configured else R.string.common_ui_open),
            onClick = {
                if (x86Id > 0) openContainerDefaultSettings(x86Id, "x86")
            },
            enabled = x86Id > 0
        )
        Spacer(Modifier.height(12.dp))
        WizardActionCard(
            title = defaultArmContainerName.value.ifBlank { "ARM64EC container" },
            subtitle = stringResource(R.string.setup_wizard_default_settings),
            completed = defaultArmSettingsDone.value,
            buttonLabel = stringResource(if (defaultArmSettingsDone.value) R.string.setup_wizard_configured else R.string.common_ui_open),
            onClick = {
                if (armId > 0) openContainerDefaultSettings(armId, "arm64")
            },
            enabled = armId > 0
        )
    }

    @Composable
    private fun PageStores() {
        val storeState by storeLoginState

        Text(
            text = stringResource(R.string.setup_wizard_store_sign_in_optional),
            color = Color(0xFF8B949E),
            fontFamily = InterFont,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        StoreActionCard(
            name = "Steam",
            signedIn = storeState.steam,
            accent = Color(0xFF66C0F4),
            onClick = {
                steamLoginLauncher.launch(Intent(this, SteamLoginActivity::class.java))
            }
        )
        Spacer(Modifier.height(12.dp))
        StoreActionCard(
            name = "Epic Games",
            signedIn = storeState.epic,
            accent = Color(0xFF8BAFD4),
            onClick = {
                epicLoginLauncher.launch(Intent(this, EpicOAuthActivity::class.java))
            }
        )
        Spacer(Modifier.height(12.dp))
        StoreActionCard(
            name = "GOG",
            signedIn = storeState.gog,
            accent = Color(0xFFA855F7),
            onClick = {
                gogLoginLauncher.launch(Intent(this, GOGOAuthActivity::class.java))
            }
        )
        Spacer(Modifier.height(12.dp))
        StoreActionCard(
            name = "Amazon Games",
            signedIn = false,
            accent = Color(0xFFFF9900),
            onClick = {},
            enabled = false,
            buttonLabel = stringResource(R.string.common_ui_coming_soon)
        )
    }

    @Composable
    private fun WizardActionCard(
        title: String,
        subtitle: String,
        completed: Boolean,
        buttonLabel: String,
        onClick: () -> Unit,
        enabled: Boolean = true
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF161B22)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(0xFFE6EDF3),
                        fontFamily = InterFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = if (completed) Color(0xFF3FB950) else Color(0xFF8B949E),
                        fontFamily = InterFont,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = onClick,
                    enabled = enabled && !completed,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (completed) Color(0xFF1F6F43) else Color(0xFF57CBDE),
                        contentColor = if (completed) Color.White else Color.Black,
                        disabledContainerColor = if (completed) Color(0xFF1F6F43) else Color(0xFF30363D),
                        disabledContentColor = if (completed) Color.White else Color(0xFF8B949E)
                    )
                ) {
                    Text(
                        text = buttonLabel,
                        fontFamily = InterFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    @Composable
    private fun StoreActionCard(
        name: String,
        signedIn: Boolean,
        accent: Color,
        onClick: () -> Unit,
        enabled: Boolean = true,
        buttonLabel: String = if (signedIn) stringResource(R.string.common_ui_signed_in) else stringResource(R.string.common_ui_sign_in)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF161B22),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = Color(0xFFE6EDF3),
                        fontFamily = InterFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(if (signedIn) R.string.common_ui_connected else R.string.common_ui_optional),
                        color = if (signedIn) Color(0xFF3FB950) else Color(0xFF8B949E),
                        fontFamily = InterFont,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = onClick,
                    enabled = enabled && !signedIn,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (signedIn) Color(0xFF1F6F43) else accent,
                        contentColor = if (signedIn) Color.White else Color.Black,
                        disabledContainerColor = if (signedIn) Color(0xFF1F6F43) else Color(0xFF30363D),
                        disabledContentColor = if (signedIn) Color.White else Color(0xFF8B949E)
                    )
                ) {
                    Text(
                        text = buttonLabel,
                        fontFamily = InterFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    @Composable
    private fun TransferDialog(state: TransferState) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Text(
                    text = state.title,
                    fontFamily = InterFont,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3)
                )
            },
            text = {
                Column {
                    Text(
                        text = state.detail,
                        fontFamily = InterFont,
                        color = Color(0xFF8B949E),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    if (state.progress != null) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF57CBDE),
                            trackColor = Color(0xFF21262D)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${(state.progress * 100f).toInt()}%",
                            fontFamily = SyncopateFont,
                            color = Color(0xFF57CBDE),
                            fontSize = 12.sp
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color(0xFF57CBDE),
                            strokeWidth = 3.dp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${state.currentIndex} / ${state.total}",
                        fontFamily = InterFont,
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            containerColor = Color(0xFF161B22),
            shape = RoundedCornerShape(18.dp)
        )
    }
}

private fun contentVersionIdentifier(profile: ContentProfile): String {
    return ContentsManager.getEntryName(profile).substringAfter('-')
}

private fun resolveJsonDownloadUrl(url: String): String {
    val githubPrefix = "https://github.com/"
    if (!url.startsWith(githubPrefix) || "/blob/" !in url) {
        return url
    }

    val path = url.removePrefix(githubPrefix)
    val ownerRepo = path.substringBefore("/blob/")
    val blobPath = path.substringAfter("/blob/")
    val branch = blobPath.substringBefore('/')
    val filePath = blobPath.substringAfter('/', "")
    if (ownerRepo.isBlank() || branch.isBlank() || filePath.isBlank()) {
        return url
    }

    return "https://raw.githubusercontent.com/$ownerRepo/$branch/$filePath"
}

private fun runtimeDisplayLabel(profile: ContentProfile): String {
    val prefix = when (profile.type) {
        ContentProfile.ContentType.CONTENT_TYPE_WINE -> "Wine"
        ContentProfile.ContentType.CONTENT_TYPE_PROTON -> "Proton"
        else -> profile.type.toString()
    }
    val version = Regex("(?i)(?:wine|proton)-([0-9]+(?:\\.[0-9]+)?)")
        .find(profile.verName)
        ?.groupValues
        ?.getOrNull(1)
        ?: profile.verName
    return "$prefix $version"
}
