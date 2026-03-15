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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

        private const val GAME_NATIVE_TAG_API =
            "https://api.github.com/repos/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/tags/GameNative"
        private const val GAME_NATIVE_MIN_UPDATED_AT = "2026-03-12T23:59:59Z"

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
            val preferredId = getDefaultX86ContainerId(context)
            if (preferredId > 0) {
                containerManager.getContainerById(preferredId)?.let { return it }
            }
            return containerManager.containers.firstOrNull()
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

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    private data class ProtonSpec(
        val label: String,
        val fixedUrl: String,
        val fallbackPattern: Regex,
        val containerDisplayName: (ContentProfile) -> String,
        val persistContainerId: (Context, Int) -> Unit
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
            label = "VKD3D Proton 3.0b",
            type = ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Vk3dk/Vk3dk-proton-3.0b.wcp",
            nameHint = "vk3dk-proton-3.0b"
        ),
        PackageSpec(
            label = "FEX 2603",
            type = ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-FEX/FEX-2603.wcp",
            nameHint = "fex-2603"
        ),
        PackageSpec(
            label = "Box64 0.4.1 fix",
            type = ContentProfile.ContentType.CONTENT_TYPE_BOX64,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Box64/Box64-0.4.1-fix.wcp",
            nameHint = "box64-0.4.1-fix"
        ),
        PackageSpec(
            label = "Wowbox64 0.4.1",
            type = ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
            url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-wowbox64/Wowbox64-0.4.1.wcp",
            nameHint = "wowbox64-0.4.1"
        )
    )

    private val x86ProtonSpec = ProtonSpec(
        label = "Recommended x86-64",
        fixedUrl = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/GameNative/proton-10.0-4-x86_64.ref4ik.wcp",
        fallbackPattern = Regex("^proton-10\\.0-4-x86_64.*\\.wcp$", RegexOption.IGNORE_CASE),
        containerDisplayName = { profile ->
            "Proton ${extractProtonMajor(profile.verName)} x86-64"
        },
        persistContainerId = ::saveDefaultX86ContainerId
    )

    private val arm64ProtonSpec = ProtonSpec(
        label = "Recommended ARM64EC",
        fixedUrl = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/GameNative/proton-BE-arm64ec-b310f0c.wcp",
        fallbackPattern = Regex("^proton-BE-arm64ec.*\\.wcp$", RegexOption.IGNORE_CASE),
        containerDisplayName = { profile ->
            "Proton ${extractProtonMajor(profile.verName)} BE Arm64EC"
        },
        persistContainerId = ::saveDefaultArm64ContainerId
    )

    private val storageGranted = mutableStateOf(false)
    private val notifGranted = mutableStateOf(false)
    private val notifDenied = mutableStateOf(false)

    private val pageIndex = mutableIntStateOf(0)
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

    private var pendingContainerSettingsType: String? = null

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
    }

    private fun refreshWizardState() {
        val imageFs = ImageFs.find(this)
        imageFsDone.value = imageFs.isValid && imageFs.version >= ImageFsInstaller.LATEST_VERSION.toInt()

        val preferences = prefs(this)
        val contentsManager = ContentsManager(this)
        contentsManager.syncContents()

        recommendedComponentsDone.value =
            preferences.getBoolean(KEY_RECOMMENDED_COMPONENTS_DONE, false) ||
                recommendedComponents.all { isPackageInstalled(contentsManager, it) }
        if (recommendedComponentsDone.value) {
            preferences.edit().putBoolean(KEY_RECOMMENDED_COMPONENTS_DONE, true).apply()
        }

        driversVisited.value = preferences.getBoolean(KEY_DRIVERS_VISITED, false)

        val containerManager = ContainerManager(this)
        val x86Container = containerManager.getContainerById(getDefaultX86ContainerId(this))
        val armContainer = containerManager.getContainerById(getDefaultArm64ContainerId(this))

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
                    recommendedComponents.forEachIndexed { index, spec ->
                        val profile = downloadAndInstallPackage(spec, index, recommendedComponents.size)
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

    private fun installRecommendedProton(spec: ProtonSpec) {
        if (transferState.value != null) return

        lifecycleScope.launch {
            wizardError.value = null
            val created = withContext(Dispatchers.IO) {
                try {
                    transferState.value = TransferState(
                        title = spec.label,
                        detail = "Preparing download",
                        currentIndex = 0,
                        total = 2
                    )

                    var resolvedUrl = spec.fixedUrl
                    var downloaded = downloadFileToCache(
                        label = spec.label,
                        url = resolvedUrl,
                        currentIndex = 1,
                        total = 2
                    )
                    if (downloaded == null) {
                        resolvedUrl = resolveFallbackProtonUrl(spec)
                        if (resolvedUrl != spec.fixedUrl) {
                            downloaded = downloadFileToCache(
                                label = spec.label,
                                url = resolvedUrl,
                                currentIndex = 1,
                                total = 2
                            )
                        }
                    }
                    if (downloaded == null) return@withContext null

                    transferState.value = TransferState(
                        title = spec.label,
                        detail = "Installing package",
                        currentIndex = 2,
                        total = 2,
                        progress = null
                    )

                    val profile = installDownloadedPackage(downloaded, resolvedUrl)
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
            title = "Recommended Components",
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
            title = "Recommended Components",
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
        val success = Downloader.downloadFile(url, output) { downloadedBytes, totalBytes ->
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

    private fun resolveFallbackProtonUrl(spec: ProtonSpec): String {
        val json = Downloader.downloadString(GAME_NATIVE_TAG_API) ?: return spec.fixedUrl
        val assets = JSONObject(json).optJSONArray("assets") ?: return spec.fixedUrl

        var selectedUrl: String? = null
        var selectedUpdatedAt = ""
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val updatedAt = asset.optString("updated_at")
            if (!spec.fallbackPattern.matches(name)) continue
            if (updatedAt <= GAME_NATIVE_MIN_UPDATED_AT) continue
            if (updatedAt > selectedUpdatedAt) {
                selectedUpdatedAt = updatedAt
                selectedUrl = asset.optString("browser_download_url")
            }
        }

        return selectedUrl ?: spec.fixedUrl
    }

    private fun ensureContainerForProfile(profile: ContentProfile, desiredName: String): Container {
        val containerManager = ContainerManager(this)
        containerManager.containers.firstOrNull { it.name == desiredName }?.let {
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
                    resolvePreferredContentVersion(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_DXVK, DefaultVersion.DXVK)
                ),
                ',',
                "vkd3dVersion",
                resolvePreferredContentVersion(contentsManager, ContentProfile.ContentType.CONTENT_TYPE_VKD3D, DefaultVersion.VKD3D)
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
        fallback: String
    ): String {
        val preferenceKey = "last_content_${type.toString().lowercase()}"
        val preferred = prefs(this).getString(preferenceKey, "") ?: ""
        val installedProfiles = manager.getProfiles(type).orEmpty().filter { it.isInstalled }
        if (preferred.isNotBlank() && installedProfiles.any { contentVersionIdentifier(it) == preferred }) {
            return preferred
        }

        val newestInstalled = installedProfiles.maxWithOrNull(
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

    private fun finishToAdvancedComponents() {
        markSetupComplete(this)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("selected_menu_item_id", R.id.main_menu_contents)
        )
        finish()
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
        val scrollState = rememberScrollState()
        val canGoNext = when (page) {
            0 -> storageGranted.value && imageFsDone.value
            1 -> recommendedComponentsDone.value && driversVisited.value
            2 -> x86ProtonDone.value && arm64ProtonDone.value
            3 -> defaultX86SettingsDone.value && defaultArmSettingsDone.value
            else -> true
        }

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
                Text(
                    text = "Setup Wizard",
                    color = Color(0xFFE6EDF3),
                    fontFamily = SyncopateFont,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Step ${page + 1} of 5",
                    color = Color(0xFF8B949E),
                    fontFamily = InterFont,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(18.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .widthIn(max = 720.dp)
                ) {
                    when (page) {
                        0 -> PagePermissions()
                        1 -> PageComponents()
                        2 -> PageWineAndProton()
                        3 -> PageDefaultSettings()
                        4 -> PageStores()
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
                    if (page == 1) {
                        OutlinedButton(
                            onClick = { finishToAdvancedComponents() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE6EDF3)
                            )
                        ) {
                            Text("Advanced User", fontFamily = InterFont)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { if (page > 0) pageIndex.intValue -= 1 },
                            enabled = page > 0,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE6EDF3),
                                disabledContentColor = Color(0xFF6E7681)
                            )
                        ) {
                            Text("Back", fontFamily = InterFont)
                        }
                    }

                    if (page < 4) {
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
                            Text("Next", fontFamily = InterFont, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { finishWizard() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                        ) {
                            Text("Finish", fontFamily = InterFont, fontWeight = FontWeight.Bold)
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
        Text(
            text = "Required Access",
            color = Color(0xFFE6EDF3),
            fontFamily = InterFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                WizardActionCard(
                    title = "Allow all file access",
                    subtitle = "Required",
                    completed = storageGranted.value,
                    buttonLabel = if (storageGranted.value) "Granted" else "Grant",
                    onClick = { requestFileAccess() }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                WizardActionCard(
                    title = "Notifications",
                    subtitle = "Optional",
                    completed = notifGranted.value,
                    buttonLabel = when {
                        notifGranted.value -> "Granted"
                        notifDenied.value -> "Denied"
                        else -> "Allow"
                    },
                    onClick = { requestNotifications() }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        WizardActionCard(
            title = "Install ImageFS",
            subtitle = "Required",
            completed = imageFsDone.value,
            buttonLabel = when {
                imageFsDone.value -> "Installed"
                imageFsInstalling.value -> "Installing"
                else -> "Install ImageFS"
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
            text = "Recommended Components",
            color = Color(0xFFE6EDF3),
            fontFamily = InterFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Recommended Components downloads and installs the supported DXVK, VKD3D, FEX, Box64, and Wowbox64 packages automatically.",
            color = Color(0xFF8B949E),
            fontFamily = InterFont,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        WizardActionCard(
            title = "Recommended Components",
            subtitle = "Required",
            completed = recommendedComponentsDone.value,
            buttonLabel = if (recommendedComponentsDone.value) "Installed" else "Download + Install",
            onClick = { installRecommendedComponents() },
            enabled = transferState.value == null
        )
        Spacer(Modifier.height(12.dp))
        WizardActionCard(
            title = "Drivers",
            subtitle = "Required",
            completed = driversVisited.value,
            buttonLabel = if (driversVisited.value) "Done" else "Open Drivers",
            onClick = { openDrivers() },
            enabled = imageFsDone.value
        )
    }

    @Composable
    private fun PageWineAndProton() {
        Text(
            text = "Recommended Wine / Proton",
            color = Color(0xFFE6EDF3),
            fontFamily = InterFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Each button downloads the package, installs it, and creates its default container automatically.",
            color = Color(0xFF8B949E),
            fontFamily = InterFont,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        WizardActionCard(
            title = "Recommended x86-64",
            subtitle = "Required",
            completed = x86ProtonDone.value,
            buttonLabel = if (x86ProtonDone.value) "Ready" else "Download + Create",
            onClick = { installRecommendedProton(x86ProtonSpec) },
            enabled = transferState.value == null
        )
        Spacer(Modifier.height(12.dp))
        WizardActionCard(
            title = "Recommended ARM64EC",
            subtitle = "Required",
            completed = arm64ProtonDone.value,
            buttonLabel = if (arm64ProtonDone.value) "Ready" else "Download + Create",
            onClick = { installRecommendedProton(arm64ProtonSpec) },
            enabled = transferState.value == null
        )
    }

    @Composable
    private fun PageDefaultSettings() {
        Text(
            text = "Default Settings",
            color = Color(0xFFE6EDF3),
            fontFamily = InterFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Open each container and set the defaults you want every new game to inherit. New games will target the x86-64 container by default.",
            color = Color(0xFF8B949E),
            fontFamily = InterFont,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        val x86Id = getDefaultX86ContainerId(this)
        val armId = getDefaultArm64ContainerId(this)

        WizardActionCard(
            title = defaultX86ContainerName.value.ifBlank { "x86-64 container" },
            subtitle = "Default Settings",
            completed = defaultX86SettingsDone.value,
            buttonLabel = if (defaultX86SettingsDone.value) "Configured" else "Open",
            onClick = {
                if (x86Id > 0) openContainerDefaultSettings(x86Id, "x86")
            },
            enabled = x86Id > 0
        )
        Spacer(Modifier.height(12.dp))
        WizardActionCard(
            title = defaultArmContainerName.value.ifBlank { "ARM64EC container" },
            subtitle = "Default Settings",
            completed = defaultArmSettingsDone.value,
            buttonLabel = if (defaultArmSettingsDone.value) "Configured" else "Open",
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
            text = "Stores",
            color = Color(0xFFE6EDF3),
            fontFamily = InterFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Store sign-in is optional. Each sign-in returns here when it finishes.",
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
            buttonLabel = "Coming Soon"
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
        buttonLabel: String = if (signedIn) "Signed In" else "Sign In"
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
                        text = if (signedIn) "Connected" else "Optional",
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

private fun extractProtonMajor(verName: String): String {
    val match = Regex("proton-(\\d+)").find(verName)
    return match?.groupValues?.getOrNull(1) ?: "10"
}

private fun contentVersionIdentifier(profile: ContentProfile): String {
    return ContentsManager.getEntryName(profile).substringAfter('-')
}
