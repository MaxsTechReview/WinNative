package com.winlator.cmod.feature.setup

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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.winlator.cmod.R
import com.winlator.cmod.app.shell.UnifiedActivity
import com.winlator.cmod.feature.settings.DriversFragment
import com.winlator.cmod.runtime.container.Container
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.content.AdrenotoolsManager
import com.winlator.cmod.runtime.content.ContentProfile
import com.winlator.cmod.runtime.content.ContentsManager
import com.winlator.cmod.runtime.content.Downloader
import com.winlator.cmod.runtime.display.environment.ImageFs
import com.winlator.cmod.runtime.display.environment.ImageFsInstaller
import com.winlator.cmod.runtime.wine.DefaultVersion
import com.winlator.cmod.runtime.wine.WineInfo
import com.winlator.cmod.shared.android.AppUtils
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.io.TarCompressorUtils
import com.winlator.cmod.shared.io.TarCompressorUtils.Type
import com.winlator.cmod.shared.util.OnExtractFileListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class Particle(
    val x: Float,
    val speed: Float,
    val size: Float,
    val phaseOffset: Float,
)

private data class TabInfo(
    val key: String,
    val label: String,
    val indicatorColor: Color,
    val highlight: Boolean = false,
)

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
        fun isSetupComplete(context: Context): Boolean = prefs(context).getBoolean(KEY_SETUP_COMPLETE, false)

        @JvmStatic
        fun markSetupComplete(context: Context) {
            prefs(context).edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
        }

        @JvmStatic
        fun getPreferredGameContainer(
            context: Context,
            containerManager: ContainerManager,
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
        fun getDefaultX86ContainerId(context: Context): Int = prefs(context).getInt(KEY_DEFAULT_X86_CONTAINER_ID, 0)

        @JvmStatic
        fun getDefaultArm64ContainerId(context: Context): Int = prefs(context).getInt(KEY_DEFAULT_ARM64_CONTAINER_ID, 0)

        @JvmStatic
        fun saveDefaultX86ContainerId(
            context: Context,
            containerId: Int,
        ) {
            prefs(context).edit().putInt(KEY_DEFAULT_X86_CONTAINER_ID, containerId).apply()
        }

        @JvmStatic
        fun saveDefaultArm64ContainerId(
            context: Context,
            containerId: Int,
        ) {
            prefs(context).edit().putInt(KEY_DEFAULT_ARM64_CONTAINER_ID, containerId).apply()
        }

        @JvmStatic
        fun recordInstalledDriver(
            context: Context,
            driverId: String,
        ) {
            prefs(context).edit().putString(KEY_LAST_DRIVER_ID, driverId).apply()
        }

        @JvmStatic
        fun getLastInstalledDriverId(context: Context): String = prefs(context).getString(KEY_LAST_DRIVER_ID, "") ?: ""

        @JvmStatic
        fun recordInstalledContent(
            context: Context,
            profile: ContentProfile,
        ) {
            val key = KEY_LAST_CONTENT_PREFIX + profile.type.toString().lowercase()
            prefs(context).edit().putString(key, contentVersionIdentifier(profile)).apply()
        }

        @JvmStatic
        fun isWineVersionInstalled(
            context: Context,
            wineVersion: String?,
        ): Boolean {
            if (wineVersion.isNullOrBlank() || WineInfo.isMainWineVersion(wineVersion)) {
                return true
            }
            val contentsManager = ContentsManager(context)
            contentsManager.syncContents()
            return isWineVersionInstalled(contentsManager, wineVersion)
        }

        @JvmStatic
        fun isContainerUsable(
            context: Context,
            container: Container?,
        ): Boolean {
            if (container == null) return false
            val contentsManager = ContentsManager(context)
            contentsManager.syncContents()
            return isContainerUsable(contentsManager, container)
        }

        @JvmStatic
        fun promptToInstallWineOrCreateContainer(
            context: Context,
            missingWineVersion: String? = null,
        ) {
            val runtimeLabel = resolveWineVersionLabel(context, missingWineVersion)
            val message =
                if (runtimeLabel.isNotBlank()) {
                    context.getString(R.string.container_wine_error_not_installed, runtimeLabel)
                } else {
                    "Download a Wine/Proton package and create a container before launching games."
                }
            AppUtils.showToast(context, message, Toast.LENGTH_LONG)

            val intent =
                when {
                    !isSetupComplete(context) -> {
                        Intent(context, SetupWizardActivity::class.java)
                    }

                    hasInstalledRuntimes(context) -> {
                        Intent(context, UnifiedActivity::class.java)
                            .putExtra("selected_menu_item_id", R.id.main_menu_containers)
                    }

                    else -> {
                        Intent(context, UnifiedActivity::class.java)
                            .putExtra("selected_menu_item_id", R.id.main_menu_contents)
                    }
                }
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun isWineVersionInstalled(
            contentsManager: ContentsManager,
            wineVersion: String?,
        ): Boolean {
            if (wineVersion.isNullOrBlank() || WineInfo.isMainWineVersion(wineVersion)) {
                return true
            }
            return contentsManager.getProfileByEntryName(wineVersion)?.isInstalled == true
        }

        private fun isContainerUsable(
            contentsManager: ContentsManager,
            container: Container,
        ): Boolean = isWineVersionInstalled(contentsManager, container.wineVersion)

        private fun hasInstalledRuntimes(context: Context): Boolean = ContentsManager.hasInstalledRuntimes(context)

        private fun resolveWineVersionLabel(
            context: Context,
            wineVersion: String?,
        ): String {
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

        val provider =
            GoogleFont.Provider(
                providerAuthority = "com.google.android.gms.fonts",
                providerPackage = "com.google.android.gms",
                certificates = R.array.com_google_android_gms_fonts_certs,
            )

        val InterFont =
            FontFamily(
                Font(googleFont = GoogleFont("Inter"), fontProvider = provider),
            )

        val SyncopateFont =
            FontFamily(
                Font(googleFont = GoogleFont("Syncopate"), fontProvider = provider),
            )
    }

    private data class PackageSpec(
        val label: String,
        val type: ContentProfile.ContentType,
        val url: String,
        val nameHint: String,
    )

    private data class RuntimeSpec(
        val label: String,
        val archToken: String,
        val fallbackType: ContentProfile.ContentType,
        val fallbackUrl: String,
        val fallbackNameHint: String,
        val containerDisplayName: (ContentProfile) -> String,
        val persistContainerId: (Context, Int) -> Unit,
    )

    private data class RemotePackageSpec(
        val type: ContentProfile.ContentType,
        val verName: String,
        val remoteUrl: String,
    )

    private data class TransferState(
        val title: String,
        val detail: String,
        val currentIndex: Int,
        val total: Int,
        val progress: Float? = null,
    )

    private val recommendedComponents =
        listOf(
            PackageSpec(
                label = "DXVK 2.7.1 GPLAsync",
                type = ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Dxvk/Dxvk-2.7.1-gplasync.wcp",
                nameHint = "dxvk-2.7.1-gplasync",
            ),
            PackageSpec(
                label = "DXVK 2.7.1 ARM64EC GPLAsync",
                type = ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Arm64ec-Dxvk/Dxvk-2.7.1-arm64ec-gplasync.wcp",
                nameHint = "Dxvk-2.7.1-arm64ec-gplasync",
            ),
            PackageSpec(
                label = "VKD3D Proton 3.0b",
                type = ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Vk3dk/Vk3dk-proton-3.0b.wcp",
                nameHint = "Vk3dk-proton-3.0b",
            ),
            PackageSpec(
                label = "VKD3D ARM64EC 3.0b",
                type = ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Arm64ec-Vk3dk/Vk3dk-arm64ec-3.0b.wcp",
                nameHint = "Vk3dk-arm64ec-3.0b",
            ),
            PackageSpec(
                label = "DXVK 2.4.1 pre-reg",
                type = ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Dxvk/Dxvk-2.4.1-pre-reg.wcp",
                nameHint = "Dxvk-2.4.1-pre-reg",
            ),
            PackageSpec(
                label = "FEX 2604",
                type = ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
                url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-FEX/FEX-2604.wcp",
                nameHint = "FEX-2604",
            ),
            PackageSpec(
                label = "Box64 0.4.1 fix",
                type = ContentProfile.ContentType.CONTENT_TYPE_BOX64,
                url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-Box64/Box64-0.4.1-fix.wcp",
                nameHint = "Box64-0.4.1-fix",
            ),
            PackageSpec(
                label = "Wowbox64 0.4.1",
                type = ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
                url = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Stable-wowbox64/Wowbox64-0.4.1.wcp",
                nameHint = "Wowbox64-0.4.1",
            ),
        )

    private val x86ProtonSpec =
        RuntimeSpec(
            label = "Recommended x86-64",
            archToken = "x86_64",
            fallbackType = ContentProfile.ContentType.CONTENT_TYPE_WINE,
            fallbackUrl = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/Wine/wine-9.20-x86_64.wcp",
            fallbackNameHint = "wine-9.20-x86_64",
            containerDisplayName = { profile ->
                "${runtimeDisplayLabel(profile)} x86-64"
            },
            persistContainerId = ::saveDefaultX86ContainerId,
        )

    private val recommendedUrlsState = mutableStateOf<Set<String>>(emptySet())

    private val fallbackRecommendedUrls: Set<String> by lazy {
        buildSet {
            recommendedComponents.forEach { add(it.url) }
            add(x86ProtonSpec.fallbackUrl)
            add(arm64ProtonSpec.fallbackUrl)
        }
    }

    private fun isRecommendedSpec(spec: RemotePackageSpec): Boolean {
        val remote = recommendedUrlsState.value
        return if (remote.isNotEmpty()) {
            spec.remoteUrl in remote
        } else {
            spec.remoteUrl in fallbackRecommendedUrls
        }
    }

    private val arm64ProtonSpec =
        RuntimeSpec(
            label = "Recommended ARM64EC",
            archToken = "arm64ec",
            fallbackType = ContentProfile.ContentType.CONTENT_TYPE_PROTON,
            fallbackUrl = "https://github.com/Xnick417x/Winlator-Bionic-Nightly-wcp/releases/download/GameNative/Proton-10-arm64ec-coffincolors.wcp",
            fallbackNameHint = "Proton-10-arm64ec-coffincolors",
            containerDisplayName = { profile ->
                "${runtimeDisplayLabel(profile)} ARM64EC"
            },
            persistContainerId = ::saveDefaultArm64ContainerId,
        )

    private val storageGranted = mutableStateOf(false)
    private val notifGranted = mutableStateOf(false)
    private val notifDenied = mutableStateOf(false)

    private val pageIndex = mutableIntStateOf(0)
    private val imageFsInstalling = mutableStateOf(false)
    private val imageFsProgress = mutableIntStateOf(0)
    private val imageFsDone = mutableStateOf(false)
    private val defaultX86SettingsDone = mutableStateOf(false)
    private val defaultArmSettingsDone = mutableStateOf(false)
    private val defaultX86ContainerName = mutableStateOf("")
    private val defaultArmContainerName = mutableStateOf("")
    private val wizardError = mutableStateOf<String?>(null)
    private val transferState = mutableStateOf<TransferState?>(null)
    private val creatingContainer = mutableStateOf(false)
    private val advancedProfiles = mutableStateListOf<RemotePackageSpec>()
    private val advancedInstalledSet = mutableStateListOf<String>()
    private val advancedContainerNames = mutableStateListOf<String>()

    private var pendingContainerSettingsType: String? = null
    private var recommendedPackageRefreshInFlight = false

    private val manageStorageLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            storageGranted.value = hasStoragePermission()
        }

    private val notifPermLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            notifGranted.value = granted
            notifDenied.value = !granted
            if (!granted && Build.VERSION.SDK_INT >= 33 &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                openNotificationSettings()
            }
        }

    private val legacyStoragePermLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            storageGranted.value =
                permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

    private val containerSettingsLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            when (pendingContainerSettingsType) {
                "x86" -> prefs(this).edit().putBoolean(KEY_DEFAULT_X86_SETTINGS_DONE, true).apply()
                "arm64" -> prefs(this).edit().putBoolean(KEY_DEFAULT_ARM64_SETTINGS_DONE, true).apply()
            }
            pendingContainerSettingsType = null
            refreshWizardState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.setFragmentResultListener(
            SetupWizardDriversDialogFragment.RESULT_KEY,
            this,
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
        loadAdvancedProfiles()

        setContent {
            MaterialTheme(
                colorScheme =
                    darkColorScheme(
                        primary = Color(0xFF57CBDE),
                        secondary = Color(0xFF3B82F6),
                        background = Color(0xFF141B24),
                        surface = Color(0xFF1E252E),
                    ),
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
        val containerManager = ContainerManager(this)
        val x86Container =
            containerManager
                .getContainerById(getDefaultX86ContainerId(this))
                ?.takeIf { isContainerUsable(this, it) }
        val armContainer =
            containerManager
                .getContainerById(getDefaultArm64ContainerId(this))
                ?.takeIf { isContainerUsable(this, it) }

        defaultX86ContainerName.value = x86Container?.name ?: ""
        defaultArmContainerName.value = armContainer?.name ?: ""

        defaultX86SettingsDone.value =
            preferences.getBoolean(KEY_DEFAULT_X86_SETTINGS_DONE, false) && x86Container != null
        defaultArmSettingsDone.value =
            preferences.getBoolean(KEY_DEFAULT_ARM64_SETTINGS_DONE, false) && armContainer != null
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasNotificationPermissionSilently(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

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
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    ),
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
                contentLength +=
                    if (assetSize > 0) {
                        (assetSize * (100.0f / compressionRatio)).toLong()
                    } else {
                        800_000_000L
                    }

                try {
                    val versions = resources.getStringArray(R.array.wine_entries)
                    versions.forEach { version ->
                        val versionSize = FileUtils.getSize(this, "$version.txz")
                        contentLength +=
                            if (versionSize > 0) {
                                (versionSize * (100.0f / compressionRatio)).toLong()
                            } else {
                                100_000_000L
                            }
                    }
                } catch (_: Exception) {
                }

                val totalSize = AtomicLong()
                val listener =
                    OnExtractFileListener { file, size ->
                        if (size > 0) {
                            val total = totalSize.addAndGet(size)
                            val percent = ((total.toFloat() / contentLength) * 100f).toInt().coerceIn(0, 100)
                            runOnUiThread { imageFsProgress.intValue = percent }
                        }
                        file
                    }

                val success =
                    TarCompressorUtils.extract(
                        Type.XZ,
                        this,
                        "imagefs.txz",
                        rootDir,
                        listener,
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

    private suspend fun downloadAndInstallPackage(
        spec: PackageSpec,
        index: Int,
        total: Int,
    ): ContentProfile? {
        transferState.value =
            TransferState(
                title = getString(R.string.setup_wizard_recommended_components),
                detail = getString(R.string.setup_wizard_downloading, spec.label),
                currentIndex = index + 1,
                total = total,
                progress = 0f,
            )

        val downloaded =
            downloadFileToCache(
                label = spec.label,
                url = spec.url,
                currentIndex = index + 1,
                total = total,
            ) ?: return null

        // Show 100% briefly so the bar visually completes before switching
        transferState.value =
            TransferState(
                title = getString(R.string.setup_wizard_recommended_components),
                detail = getString(R.string.setup_wizard_downloading, spec.label),
                currentIndex = index + 1,
                total = total,
                progress = 1f,
            )
        kotlinx.coroutines.delay(500)

        transferState.value =
            TransferState(
                title = getString(R.string.setup_wizard_recommended_components),
                detail = getString(R.string.setup_wizard_installing_package, spec.label),
                currentIndex = index + 1,
                total = total,
                progress = null,
            )

        val profile = installDownloadedPackage(downloaded, spec.url)
        downloaded.delete()
        return profile
    }

    private suspend fun downloadFileToCache(
        label: String,
        url: String,
        currentIndex: Int,
        total: Int,
    ): File? =
        withContext(Dispatchers.IO) {
            val sanitized = label.lowercase().replace(Regex("[^a-z0-9]+"), "_")
            val output = File(cacheDir, "wizard_${System.currentTimeMillis()}_$sanitized.wcp")
            val listener =
                Downloader.DownloadListener { downloadedBytes, totalBytes ->
                    transferState.value =
                        TransferState(
                            title = transferState.value?.title ?: label,
                            detail = getString(R.string.setup_wizard_downloading, label),
                            currentIndex = currentIndex,
                            total = total,
                            progress =
                                if (totalBytes > 0) {
                                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    null
                                },
                        )
                }
            val success = Downloader.downloadFileWinNativeFirst(url, output, listener)
            if (success) output else null
        }

    private fun installDownloadedPackage(
        file: File,
        sourceUrl: String,
    ): ContentProfile? {
        val manager = ContentsManager(this)
        manager.syncContents()

        var extractedProfile: ContentProfile? = null
        var installedProfile: ContentProfile? = null
        var failed = false

        val callback =
            object : ContentsManager.OnInstallFinishedCallback {
                private var extracting = true

                override fun onFailed(
                    reason: ContentsManager.InstallFailedReason,
                    e: Exception?,
                ) {
                    if (reason == ContentsManager.InstallFailedReason.ERROR_EXIST && extractedProfile != null) {
                        manager.registerRemoteProfileAlias(sourceUrl, extractedProfile)
                        manager.syncContents()
                        installedProfile = manager.getProfileByEntryName(
                            ContentsManager.getEntryName(extractedProfile),
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
        val componentTypes =
            setOf(
                ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
                ContentProfile.ContentType.CONTENT_TYPE_BOX64,
                ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
            )
        val remoteSpecs =
            fetchRecommendedPackages()
                .filter { it.type in componentTypes }
                .map {
                    PackageSpec(
                        label = it.verName,
                        type = it.type,
                        url = it.remoteUrl,
                        nameHint = it.verName,
                    )
                }
        return remoteSpecs.ifEmpty { recommendedComponents }
    }

    private fun resolveRecommendedRuntimeSpec(spec: RuntimeSpec): PackageSpec {
        val resolved =
            fetchRecommendedPackages().firstOrNull {
                (
                    it.type == ContentProfile.ContentType.CONTENT_TYPE_WINE ||
                        it.type == ContentProfile.ContentType.CONTENT_TYPE_PROTON
                ) &&
                    it.verName.contains(spec.archToken, ignoreCase = true)
            }

        if (resolved != null) {
            return PackageSpec(
                label = spec.label,
                type = resolved.type,
                url = resolved.remoteUrl,
                nameHint = resolved.verName,
            )
        }

        return PackageSpec(
            label = spec.label,
            type = spec.fallbackType,
            url = spec.fallbackUrl,
            nameHint = spec.fallbackNameHint,
        )
    }

    private fun fetchRecommendedPackages(): List<RemotePackageSpec> {
        val json = Downloader.downloadString(resolveJsonDownloadUrl(DEFAULT_JSON_URL))
        if (!json.isNullOrBlank()) {
            prefs(this).edit().putString(KEY_DEFAULT_JSON_CACHE, json).apply()
            val specs = parseRecommendedPackages(json)
            if (specs.isNotEmpty()) {
                recommendedUrlsState.value = specs.map { it.remoteUrl }.toSet()
            }
            return specs
        }
        val cached = getCachedRecommendedPackages()
        if (cached.isNotEmpty()) {
            recommendedUrlsState.value = cached.map { it.remoteUrl }.toSet()
        }
        return cached
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

    private fun getCachedRecommendedComponentSpecs(): List<PackageSpec> =
        getCachedRecommendedPackages()
            .filter {
                it.type != ContentProfile.ContentType.CONTENT_TYPE_WINE &&
                    it.type != ContentProfile.ContentType.CONTENT_TYPE_PROTON
            }.map {
                PackageSpec(
                    label = it.verName,
                    type = it.type,
                    url = it.remoteUrl,
                    nameHint = it.verName,
                )
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

    private fun ensureContainerForProfile(
        profile: ContentProfile,
        desiredName: String,
    ): Container {
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
        val data =
            JSONObject().apply {
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
        val normalizedDrives =
            com.winlator.cmod.runtime.wine.WineUtils.normalizePersistentDrives(
                this,
                container.drives ?: Container.DEFAULT_DRIVES,
            )

        container.setGraphicsDriver(Container.DEFAULT_GRAPHICS_DRIVER)
        container.setDrives(normalizedDrives)
        container.setGraphicsDriverConfig(
            replaceDelimitedConfigValue(
                Container.DEFAULT_GRAPHICSDRIVERCONFIG,
                ';',
                "version",
                resolvePreferredDriverVersion(),
            ),
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
                        if (isArm64) null else Regex("arm64ec", RegexOption.IGNORE_CASE),
                    ),
                ),
                ',',
                "vkd3dVersion",
                resolvePreferredContentVersion(
                    contentsManager,
                    ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                    DefaultVersion.VKD3D,
                    if (isArm64) Regex("arm64ec", RegexOption.IGNORE_CASE) else null,
                    if (isArm64) null else Regex("arm64ec", RegexOption.IGNORE_CASE),
                ),
            ),
        )

        if (isArm64) {
            container.setEmulator("fexcore")
            container.setEmulator64("fexcore")
            container.setBox64Version(
                resolvePreferredContentVersion(
                    contentsManager,
                    ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
                    DefaultVersion.WOWBOX64,
                ),
            )
            container.setFEXCoreVersion(
                resolvePreferredContentVersion(
                    contentsManager,
                    ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
                    DefaultVersion.FEXCORE,
                ),
            )
        } else {
            container.setEmulator("box64")
            container.setEmulator64("box64")
            container.setBox64Version(
                resolvePreferredContentVersion(
                    contentsManager,
                    ContentProfile.ContentType.CONTENT_TYPE_BOX64,
                    DefaultVersion.BOX64,
                ),
            )
            container.setFEXCoreVersion(
                resolvePreferredContentVersion(
                    contentsManager,
                    ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
                    DefaultVersion.FEXCORE,
                ),
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
            if (com.winlator.cmod.runtime.system.GPUInformation
                    .isDriverSupported(DefaultVersion.WRAPPER_ADRENO, this)
            ) {
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
        excludePattern: Regex? = null,
    ): String {
        val preferenceKey = "last_content_${type.toString().lowercase()}"
        val preferred = prefs(this).getString(preferenceKey, "") ?: ""
        val installedProfiles = manager.getProfiles(type).orEmpty().filter { it.isInstalled }
        val matchingProfiles =
            installedProfiles
                .filter { profile ->
                    val versionName = profile.verName
                    (includePattern == null || includePattern.containsMatchIn(versionName)) &&
                        (excludePattern == null || !excludePattern.containsMatchIn(versionName))
                }.ifEmpty { installedProfiles }

        if (preferred.isNotBlank() && matchingProfiles.any { contentVersionIdentifier(it) == preferred }) {
            return preferred
        }

        val newestInstalled =
            matchingProfiles.maxWithOrNull(
                compareBy<ContentProfile> { it.verCode }.thenBy { it.verName.lowercase() },
            )
        return newestInstalled?.let(::contentVersionIdentifier) ?: fallback
    }

    private fun replaceDelimitedConfigValue(
        config: String,
        delimiter: Char,
        key: String,
        value: String,
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

    private fun isPackageInstalled(
        manager: ContentsManager,
        spec: PackageSpec,
    ): Boolean =
        manager.getProfiles(spec.type).orEmpty().any { profile ->
            profile.isInstalled && profile.verName.contains(spec.nameHint, ignoreCase = true)
        }

    private fun openDrivers() {
        if (supportFragmentManager.findFragmentByTag(SetupWizardDriversDialogFragment.TAG) == null) {
            SetupWizardDriversDialogFragment().show(
                supportFragmentManager,
                SetupWizardDriversDialogFragment.TAG,
            )
        }
    }

    private fun loadAdvancedProfiles() {
        if (advancedProfiles.isNotEmpty()) return
        lifecycleScope.launch {
            val profiles =
                withContext(Dispatchers.IO) {
                    // Fetch recommended (default.json) for marking recommendations
                    val recommended = fetchRecommendedPackages()
                    // Fetch full catalog (content.json) for all categories
                    val fullCatalog =
                        parseRecommendedPackages(
                            Downloader.downloadString(ContentsManager.REMOTE_PROFILES),
                        )
                    // Merge: start with recommended, then add any full catalog entries not already present
                    val seen = recommended.map { it.remoteUrl }.toMutableSet()
                    val merged = recommended.toMutableList()
                    for (spec in fullCatalog) {
                        if (spec.remoteUrl !in seen) {
                            seen.add(spec.remoteUrl)
                            merged.add(spec)
                        }
                    }
                    merged
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
            val installedByName =
                manager.getProfiles(spec.type).orEmpty().any {
                    it.isInstalled && (
                        it.verName.equals(spec.verName, ignoreCase = true) ||
                            it.verName.contains(spec.verName, ignoreCase = true) ||
                            spec.verName.contains(it.verName, ignoreCase = true)
                    )
                }
            val installedByUrl = manager.isRemoteUrlInstalled(spec.remoteUrl)
            if (installedByName || installedByUrl) advancedInstalledSet.add(spec.verName)
        }
        // Also refresh container names for default settings page
        val containerManager = ContainerManager(this)
        advancedContainerNames.clear()
        containerManager.containers.forEach {
            advancedContainerNames.add(it.name)
        }
    }

    private fun installAllRecommended() {
        if (transferState.value != null) return
        val pending =
            advancedProfiles
                .filter { isRecommendedSpec(it) && it.verName !in advancedInstalledSet }
        if (pending.isEmpty()) return

        lifecycleScope.launch {
            wizardError.value = null
            for ((index, spec) in pending.withIndex()) {
                val profile =
                    withContext(Dispatchers.IO) {
                        try {
                            transferState.value =
                                TransferState(
                                    title = getString(R.string.setup_wizard_recommended_components),
                                    detail = getString(R.string.setup_wizard_downloading, spec.verName),
                                    currentIndex = index + 1,
                                    total = pending.size,
                                    progress = 0f,
                                )
                            val downloaded =
                                downloadFileToCache(
                                    label = spec.verName,
                                    url = spec.remoteUrl,
                                    currentIndex = index + 1,
                                    total = pending.size,
                                )
                            if (downloaded == null) return@withContext null

                            transferState.value =
                                TransferState(
                                    title = getString(R.string.setup_wizard_recommended_components),
                                    detail = getString(R.string.setup_wizard_downloading, spec.verName),
                                    currentIndex = index + 1,
                                    total = pending.size,
                                    progress = 1f,
                                )
                            kotlinx.coroutines.delay(500)

                            transferState.value =
                                TransferState(
                                    title = getString(R.string.setup_wizard_recommended_components),
                                    detail = getString(R.string.setup_wizard_installing_package, spec.verName),
                                    currentIndex = index + 1,
                                    total = pending.size,
                                    progress = null,
                                )

                            val installed = installDownloadedPackage(downloaded, spec.remoteUrl)
                            downloaded.delete()
                            installed
                        } catch (e: Exception) {
                            wizardError.value = "Install failed: ${e.message}"
                            null
                        }
                    }
                if (profile != null) {
                    if (spec.verName !in advancedInstalledSet) {
                        advancedInstalledSet.add(spec.verName)
                    }
                } else {
                    break
                }
            }
            transferState.value = null
            refreshAdvancedInstalledSet()
            refreshWizardState()
        }
    }

    private fun installAdvancedComponent(spec: RemotePackageSpec) {
        if (transferState.value != null) return
        lifecycleScope.launch {
            wizardError.value = null
            val profile =
                withContext(Dispatchers.IO) {
                    try {
                        transferState.value =
                            TransferState(
                                title = spec.verName,
                                detail = getString(R.string.downloads_queue_preparing_download),
                                currentIndex = 1,
                                total = 1,
                            )
                        val downloaded =
                            downloadFileToCache(
                                label = spec.verName,
                                url = spec.remoteUrl,
                                currentIndex = 1,
                                total = 1,
                            )
                        if (downloaded == null) return@withContext null

                        // Show 100% briefly so the bar visually completes
                        transferState.value =
                            TransferState(
                                title = spec.verName,
                                detail = getString(R.string.setup_wizard_downloading, spec.verName),
                                currentIndex = 1,
                                total = 1,
                                progress = 1f,
                            )
                        kotlinx.coroutines.delay(500)

                        transferState.value =
                            TransferState(
                                title = spec.verName,
                                detail = getString(R.string.setup_wizard_installing),
                                currentIndex = 1,
                                total = 1,
                                progress = null,
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
                // Eagerly mark as installed so UI updates immediately
                if (spec.verName !in advancedInstalledSet) {
                    advancedInstalledSet.add(spec.verName)
                }
                refreshAdvancedInstalledSet()
                refreshWizardState()
            }
        }
    }

    private fun openContainerDefaultSettings(
        containerId: Int,
        type: String,
    ) {
        pendingContainerSettingsType = type
        containerSettingsLauncher.launch(
            Intent(this, UnifiedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("edit_container_id", containerId),
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
        startActivity(
            Intent(this, UnifiedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        com.winlator.cmod.shared.android.AppUtils
            .applyOpenActivityTransition(this, 0, 0)
        finish()
    }

    @Composable
    private fun SetupWizardScreen() {
        val page by pageIndex
        val totalPages = 3
        val pageTitle =
            when (page) {
                0 -> stringResource(R.string.setup_wizard_required_access)
                1 -> stringResource(R.string.setup_wizard_select_components)
                2 -> stringResource(R.string.setup_wizard_containers)
                else -> ""
            }
        val canGoNext =
            when (page) {
                0 -> storageGranted.value && imageFsDone.value
                else -> true
            }
        val lastPage = totalPages - 1

        // Particle seeds — stable across recomposition
        val particles =
            remember {
                List(20) { i ->
                    val hash = ((i * 7919 + 104729) % 10000) / 10000f
                    Particle(
                        x = ((i * 3571 + 7321) % 10000) / 10000f,
                        speed = 0.6f + hash * 0.4f,
                        size = 1f + hash * 1.5f,
                        phaseOffset = hash * 6.2832f,
                    )
                }
            }

        // Page-reactive orb anchors — orbs smoothly migrate when page changes
        val orb1TargetX =
            when (page) {
                0 -> 0.25f
                1 -> 0.50f
                else -> 0.75f
            }
        val orb1TargetY =
            when (page) {
                0 -> 0.30f
                1 -> 0.20f
                else -> 0.25f
            }
        val orb2TargetX =
            when (page) {
                0 -> 0.70f
                1 -> 0.30f
                else -> 0.20f
            }
        val orb2TargetY =
            when (page) {
                0 -> 0.70f
                1 -> 0.55f
                else -> 0.75f
            }
        val orb3TargetX =
            when (page) {
                0 -> 0.50f
                1 -> 0.75f
                else -> 0.50f
            }
        val orb3TargetY =
            when (page) {
                0 -> 0.50f
                1 -> 0.80f
                else -> 0.50f
            }

        val orbAnim = tween<Float>(2000, easing = EaseInOut)
        val o1x by animateFloatAsState(orb1TargetX, orbAnim, label = "o1x")
        val o1y by animateFloatAsState(orb1TargetY, orbAnim, label = "o1y")
        val o2x by animateFloatAsState(orb2TargetX, orbAnim, label = "o2x")
        val o2y by animateFloatAsState(orb2TargetY, orbAnim, label = "o2y")
        val o3x by animateFloatAsState(orb3TargetX, orbAnim, label = "o3x")
        val o3y by animateFloatAsState(orb3TargetY, orbAnim, label = "o3y")

        val infiniteTransition = rememberInfiniteTransition(label = "bgGlow")
        val phase1 =
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 6.2832f,
                animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
                label = "phase1",
            )
        val phase2 =
            infiniteTransition.animateFloat(
                initialValue = 6.2832f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(38000, easing = LinearEasing), RepeatMode.Restart),
                label = "phase2",
            )
        val pulse =
            infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(8000, easing = EaseInOut), RepeatMode.Reverse),
                label = "pulse",
            )
        val particlePhase =
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
                label = "particlePhase",
            )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111822))
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        val p1 = phase1.value
                        val p2 = phase2.value
                        val p = pulse.value

                        // Orb 1 — cyan, page-reactive + gentle drift
                        val c1 =
                            Offset(
                                w * (o1x + 0.04f * cos(p1)),
                                h * (o1y + 0.03f * sin(p1)),
                            )
                        drawCircle(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF57CBDE).copy(alpha = 0.04f * p),
                                            Color(0xFF57CBDE).copy(alpha = 0.015f * p),
                                            Color.Transparent,
                                        ),
                                    center = c1,
                                    radius = w * 0.6f,
                                ),
                            radius = w * 0.6f,
                            center = c1,
                        )

                        // Orb 2 — blue, page-reactive + gentle drift
                        val c2 =
                            Offset(
                                w * (o2x + 0.04f * cos(p2)),
                                h * (o2y + 0.03f * sin(p2)),
                            )
                        drawCircle(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF3B82F6).copy(alpha = 0.035f * p),
                                            Color(0xFF3B82F6).copy(alpha = 0.01f * p),
                                            Color.Transparent,
                                        ),
                                    center = c2,
                                    radius = w * 0.55f,
                                ),
                            radius = w * 0.55f,
                            center = c2,
                        )

                        // Orb 3 — teal accent, page-reactive + gentle drift
                        val c3 =
                            Offset(
                                w * (o3x + 0.03f * sin(p1 * 0.7f)),
                                h * (o3y + 0.03f * cos(p2 * 0.6f)),
                            )
                        drawCircle(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF2DD4BF).copy(alpha = 0.02f * p),
                                            Color.Transparent,
                                        ),
                                    center = c3,
                                    radius = w * 0.45f,
                                ),
                            radius = w * 0.45f,
                            center = c3,
                        )

                        // Floating particles
                        val pp = particlePhase.value
                        particles.forEach { pt ->
                            val t = (pp * pt.speed + pt.phaseOffset) % 1f
                            val py = h * (1f - t)
                            val px = w * pt.x + w * 0.02f * sin((t * 2f * PI).toFloat() + pt.phaseOffset)
                            // Fade in at bottom, fade out at top
                            val alpha =
                                when {
                                    t < 0.15f -> t / 0.15f
                                    t > 0.85f -> (1f - t) / 0.15f
                                    else -> 1f
                                } * 0.12f
                            drawCircle(
                                color = Color(0xFF57CBDE).copy(alpha = alpha),
                                radius = pt.size.dp.toPx(),
                                center = Offset(px, py),
                            )
                        }
                    },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                // ---- Header ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WinNative",
                            color = Color(0xFFE6EDF3),
                            fontFamily = SyncopateFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.setup_wizard_title).uppercase(),
                                color = Color(0xFF57CBDE),
                                fontFamily = InterFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp,
                                letterSpacing = 1.5.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier =
                                    Modifier
                                        .size(3.dp)
                                        .background(Color(0xFF4A5260), RoundedCornerShape(2.dp)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = pageTitle,
                                color = Color(0xFF8B949E),
                                fontFamily = InterFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    StepIndicator(current = page, total = totalPages)
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF222D3D)),
                )
                Spacer(Modifier.height(12.dp))

                // ---- Content ----
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val isCompact = maxWidth < 500.dp
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            val forward = targetState > initialState
                            val enter =
                                slideInHorizontally(
                                    animationSpec = tween(220),
                                ) { if (forward) it / 4 else -it / 4 } +
                                    fadeIn(tween(160, delayMillis = 40))
                            val exit =
                                slideOutHorizontally(
                                    animationSpec = tween(220),
                                ) { if (forward) -it / 4 else it / 4 } +
                                    fadeOut(tween(140))
                            (enter togetherWith exit).using(
                                SizeTransform(clip = false),
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "pageTransition",
                    ) { targetPage ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (targetPage) {
                                0 -> PagePermissions(isCompact)
                                1 -> PageAdvancedComponents(isCompact)
                                2 -> PageDefaultSettings()
                            }
                        }
                    }

                    wizardError.value?.let { message ->
                        Text(
                            text = message,
                            color = Color(0xFFFF7B72),
                            fontFamily = InterFont,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ---- Action bar ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GhostPillButton(
                        label = stringResource(R.string.common_ui_back),
                        enabled = page > 0 && transferState.value == null,
                        onClick = { if (page > 0) pageIndex.intValue -= 1 },
                    )

                    if (page < lastPage) {
                        AccentPillButton(
                            label = stringResource(R.string.setup_wizard_next),
                            enabled = canGoNext && transferState.value == null,
                            onClick = { if (canGoNext) pageIndex.intValue += 1 },
                        )
                    } else {
                        AccentPillButton(
                            label = stringResource(R.string.setup_wizard_finish),
                            enabled = transferState.value == null && !creatingContainer.value,
                            onClick = { finishWizard() },
                        )
                    }
                }
            }

            // Transfer strip — floats at bottom, outside the Column
            val transfer = transferState.value
            if (transfer != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    TransferStrip(transfer)
                }
            }
        }
    }

    @Composable
    private fun TransferStrip(state: TransferState) {
        val animatedProgress by animateFloatAsState(
            targetValue = state.progress ?: 0f,
            animationSpec = tween(durationMillis = 400, easing = LinearEasing),
            label = "transferProgress",
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF182030), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF254558), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(Color(0xFF57CBDE), RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.title,
                        color = Color(0xFFF0F6FC),
                        fontFamily = InterFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (state.total > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${state.currentIndex}/${state.total}",
                            color = Color(0xFF57CBDE),
                            fontFamily = SyncopateFont,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = state.detail,
                    color = Color(0xFFB8C5D1),
                    fontFamily = InterFont,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                if (state.progress != null) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color(0xFF293B4D), RoundedCornerShape(3.dp)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color(0xFF57CBDE), RoundedCornerShape(3.dp)),
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                        color = Color(0xFF57CBDE),
                        trackColor = Color(0xFF293B4D),
                    )
                }
            }
            if (state.progress != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${(animatedProgress * 100f).toInt()}%",
                    color = Color(0xFF57CBDE),
                    fontFamily = SyncopateFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }

    @Composable
    private fun StepIndicator(
        current: Int,
        total: Int,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until total) {
                val active = i == current
                val completed = i < current
                val reached = active || completed
                val bg = if (reached) Color(0xFF57CBDE) else Color(0xFF111822)
                val borderC = if (reached) Color(0xFF57CBDE) else Color(0xFF323C4A)
                val textC = if (reached) Color(0xFF111822) else Color(0xFF8B949E)
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .background(bg, RoundedCornerShape(12.dp))
                            .border(1.5.dp, borderC, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${i + 1}",
                        color = textC,
                        fontFamily = InterFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
                if (i < total - 1) {
                    Box(
                        modifier =
                            Modifier
                                .width(18.dp)
                                .height(2.dp)
                                .background(if (i < current) Color(0xFF57CBDE) else Color(0xFF323C4A)),
                    )
                }
            }
        }
    }

    @Composable
    private fun GhostPillButton(
        label: String,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (enabled) Color(0xFF434D5C) else Color(0xFF222D3D)),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFE6EDF3),
                    disabledContentColor = Color(0xFF434D5C),
                ),
        ) {
            Text(label, fontFamily = InterFont, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    private fun AccentPillButton(
        label: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        val borderColor by animateColorAsState(
            targetValue = if (enabled) Color(0xFF57CBDE) else Color(0xFF222D3D),
            animationSpec = tween(300),
            label = "accentBorder",
        )
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, borderColor),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF57CBDE),
                    disabledContentColor = Color(0xFF4A5260),
                ),
        ) {
            Text(label, fontFamily = InterFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    private fun PagePermissions(isCompact: Boolean) {
        val fileAccessCard: @Composable (Modifier) -> Unit = { mod ->
            WizardActionCard(
                modifier = mod,
                title = stringResource(R.string.setup_wizard_allow_file_access),
                subtitle = stringResource(R.string.common_ui_required),
                completed = storageGranted.value,
                buttonLabel = stringResource(if (storageGranted.value) R.string.setup_wizard_granted else R.string.setup_wizard_grant),
                onClick = { requestFileAccess() },
            )
        }
        val notifCard: @Composable (Modifier) -> Unit = { mod ->
            WizardActionCard(
                modifier = mod,
                title = stringResource(R.string.common_ui_notifications),
                subtitle = stringResource(R.string.common_ui_optional),
                completed = notifGranted.value,
                buttonLabel =
                    when {
                        notifGranted.value -> stringResource(R.string.setup_wizard_granted)
                        notifDenied.value -> stringResource(R.string.setup_wizard_denied)
                        else -> stringResource(R.string.setup_wizard_allow)
                    },
                onClick = { requestNotifications() },
            )
        }
        val systemCard: @Composable (Modifier) -> Unit = { mod ->
            WizardActionCard(
                modifier = mod,
                title = stringResource(R.string.setup_wizard_install_system_files),
                subtitle = stringResource(R.string.common_ui_required),
                completed = imageFsDone.value,
                buttonLabel =
                    when {
                        imageFsDone.value -> stringResource(R.string.common_ui_installed)
                        imageFsInstalling.value -> "${imageFsProgress.intValue}%"
                        else -> stringResource(R.string.setup_wizard_install_system_files)
                    },
                onClick = { installImageFs() },
                enabled = !imageFsInstalling.value,
                progress = if (imageFsInstalling.value) imageFsProgress.intValue / 100f else null,
            )
        }

        if (isCompact) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                fileAccessCard(Modifier.fillMaxWidth())
                notifCard(Modifier.fillMaxWidth())
                systemCard(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                fileAccessCard(Modifier.weight(1f))
                notifCard(Modifier.weight(1f))
                systemCard(Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun PageAdvancedComponents(isCompact: Boolean) {
        val typeOrder =
            listOf(
                ContentProfile.ContentType.CONTENT_TYPE_WINE,
                ContentProfile.ContentType.CONTENT_TYPE_PROTON,
                ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                ContentProfile.ContentType.CONTENT_TYPE_BOX64,
                ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
                ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64,
            )
        val typeLabels =
            mapOf(
                ContentProfile.ContentType.CONTENT_TYPE_WINE to "Wine",
                ContentProfile.ContentType.CONTENT_TYPE_PROTON to "Proton",
                ContentProfile.ContentType.CONTENT_TYPE_DXVK to "DXVK",
                ContentProfile.ContentType.CONTENT_TYPE_VKD3D to "VKD3D",
                ContentProfile.ContentType.CONTENT_TYPE_BOX64 to "Box64",
                ContentProfile.ContentType.CONTENT_TYPE_FEXCORE to "FEXCore",
                ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64 to "Wowbox64",
            )
        val recommendedLabel = stringResource(R.string.setup_wizard_recommended_label)
        val driversLabel = stringResource(R.string.settings_drivers_title)
        var selectedTab by remember { mutableStateOf("recommended") }

        // Build tab keys/labels
        val tabs =
            buildList {
                add(TabInfo("recommended", recommendedLabel, Color(0xFF57CBDE), highlight = true))
                add(
                    TabInfo(
                        "drivers",
                        driversLabel,
                        if (selectedTab == "drivers") Color(0xFF57CBDE) else Color(0xFF4A5568),
                    ),
                )
                typeOrder.forEach { type ->
                    val key = type.name
                    val hasInstalled = advancedProfiles.any { it.type == type && it.verName in advancedInstalledSet }
                    val indicator =
                        when {
                            selectedTab == key -> Color(0xFF57CBDE)
                            hasInstalled -> Color(0xFF3B82F6)
                            else -> Color(0xFF4A5568)
                        }
                    add(TabInfo(key, typeLabels[type] ?: type.toString(), indicator))
                }
            }

        // Content panel (shared between layouts)
        @Composable
        fun ContentPanel(modifier: Modifier) {
            Box(
                modifier =
                    modifier
                        .background(Color(0xFF182030), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF222D3D), RoundedCornerShape(12.dp))
                        .padding(10.dp),
            ) {
                when (selectedTab) {
                    "drivers" -> {
                        if (!imageFsDone.value) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.setup_wizard_system_image_not_installed),
                                    color = Color(0xFF8B949E),
                                    fontFamily = InterFont,
                                    fontSize = 12.sp,
                                )
                            }
                        } else {
                            val driversFragmentId = remember { android.view.View.generateViewId() }
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    FragmentContainerView(ctx).apply {
                                        id = driversFragmentId
                                    }
                                },
                                update = { view ->
                                    val fm = supportFragmentManager
                                    if (fm.findFragmentById(driversFragmentId) == null) {
                                        fm
                                            .beginTransaction()
                                            .replace(driversFragmentId, DriversFragment())
                                            .commitNowAllowingStateLoss()
                                    }
                                },
                            )
                        }
                    }

                    else -> {
                        val tabProfiles: List<RemotePackageSpec> =
                            if (selectedTab == "recommended") {
                                advancedProfiles
                                    .filter { isRecommendedSpec(it) }
                                    .sortedWith(compareBy({ typeOrder.indexOf(it.type) }, { it.verName }))
                            } else {
                                val type = typeOrder.firstOrNull { it.name == selectedTab }
                                if (type == null) {
                                    emptyList()
                                } else {
                                    advancedProfiles
                                        .filter { it.type == type }
                                        .sortedByDescending { isRecommendedSpec(it) }
                                }
                            }
                        when {
                            advancedProfiles.isEmpty() -> {
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color(0xFF57CBDE),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.setup_wizard_loading_components),
                                        color = Color(0xFF8B949E),
                                        fontFamily = InterFont,
                                        fontSize = 12.sp,
                                    )
                                }
                            }

                            tabProfiles.isEmpty() -> {
                                Text(
                                    text = stringResource(R.string.setup_wizard_no_components_available),
                                    color = Color(0xFF8B949E),
                                    fontFamily = InterFont,
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }

                            else -> {
                                val isRecommendedTab = selectedTab == "recommended"
                                val allRecommendedInstalled =
                                    isRecommendedTab &&
                                        tabProfiles.all { it.verName in advancedInstalledSet }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (isRecommendedTab) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.CenterEnd,
                                            ) {
                                                OutlinedButton(
                                                    onClick = { installAllRecommended() },
                                                    enabled = transferState.value == null && !allRecommendedInstalled,
                                                    shape = RoundedCornerShape(8.dp),
                                                    border =
                                                        BorderStroke(
                                                            1.dp,
                                                            if (allRecommendedInstalled) {
                                                                Color(0xFF23436F)
                                                            } else if (transferState.value != null) {
                                                                Color(0xFF222D3D)
                                                            } else {
                                                                Color(0xFF306679)
                                                            },
                                                        ),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(30.dp),
                                                    colors =
                                                        ButtonDefaults.outlinedButtonColors(
                                                            contentColor =
                                                                if (allRecommendedInstalled) {
                                                                    Color(
                                                                        0xFF3B82F6,
                                                                    )
                                                                } else {
                                                                    Color(0xFF8BB8C5)
                                                                },
                                                            disabledContentColor =
                                                                if (allRecommendedInstalled) {
                                                                    Color(
                                                                        0xFF3B82F6,
                                                                    )
                                                                } else {
                                                                    Color(0xFF4A5260)
                                                                },
                                                        ),
                                                ) {
                                                    Text(
                                                        text =
                                                            stringResource(
                                                                if (allRecommendedInstalled) {
                                                                    R.string.common_ui_installed
                                                                } else {
                                                                    R.string.setup_wizard_install_all_recommended
                                                                },
                                                            ),
                                                        fontFamily = InterFont,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 11.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    items(tabProfiles) { spec ->
                                        val installed = spec.verName in advancedInstalledSet
                                        AdvancedComponentCard(
                                            name = spec.verName,
                                            installed = installed,
                                            onClick = { installAdvancedComponent(spec) },
                                            enabled = transferState.value == null && !installed,
                                            recommended = isRecommendedSpec(spec),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun TabItem(
            tab: TabInfo,
            fillWidth: Boolean,
            fontSize: TextUnit,
        ) {
            val isSelected = selectedTab == tab.key
            val interactionSource = remember { MutableInteractionSource() }
            val bgColor = if (isSelected) Color(0xFF203448) else Color.Transparent
            val labelColor =
                when {
                    isSelected -> Color(0xFFE6EDF3)
                    tab.highlight -> Color(0xFF57CBDE)
                    else -> Color(0xFFCDD9E5)
                }
            Row(
                modifier =
                    Modifier
                        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                        .background(bgColor, RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { selectedTab = tab.key }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(tab.indicatorColor, RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.width(if (fillWidth) 8.dp else 6.dp))
                Text(
                    text = tab.label,
                    color = labelColor,
                    fontFamily = InterFont,
                    fontSize = fontSize,
                    fontWeight = if (isSelected || tab.highlight) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }

        if (isCompact) {
            // Compact: horizontal scrolling tab strip on top, content below
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF182030), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF222D3D), RoundedCornerShape(12.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEach { tab -> TabItem(tab, fillWidth = false, fontSize = 12.sp) }
                }

                ContentPanel(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                )
            }
        } else {
            // Wide: side-by-side rail + content
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Left rail
                Column(
                    modifier =
                        Modifier
                            .weight(0.38f)
                            .fillMaxHeight()
                            .background(Color(0xFF182030), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF222D3D), RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEach { tab -> TabItem(tab, fillWidth = true, fontSize = 13.sp) }
                }

                ContentPanel(
                    modifier =
                        Modifier
                            .weight(0.62f)
                            .fillMaxHeight(),
                )
            }
        }
    }

    @Composable
    private fun AdvancedComponentCard(
        name: String,
        installed: Boolean,
        onClick: () -> Unit,
        enabled: Boolean = true,
        recommended: Boolean = false,
    ) {
        val bgColor = Color(0xFF19212C)
        val outlineColor = Color(0xFF2A3443)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(bgColor, RoundedCornerShape(12.dp))
                    .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (recommended) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier =
                                Modifier
                                    .size(4.dp)
                                    .background(Color(0xFF3B82F6), RoundedCornerShape(50)),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.setup_wizard_recommended_label),
                            color = Color(0xFF8BB8C5),
                            fontFamily = InterFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    text = name,
                    color = Color(0xFFE6EDF3),
                    fontFamily = InterFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onClick,
                enabled = enabled && !installed,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (installed) Color(0xFF1C2E49) else Color(0xFF1F4659),
                        contentColor = if (installed) Color(0xFF3B82F6) else Color(0xFF57CBDE),
                        disabledContainerColor = if (installed) Color(0xFF1C2E49) else Color(0xFF1C232B),
                        disabledContentColor = if (installed) Color(0xFF3B82F6) else Color(0xFF4A5260),
                    ),
            ) {
                Text(
                    text = stringResource(if (installed) R.string.common_ui_installed else R.string.common_ui_install),
                    fontFamily = InterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
        }
    }

    @Composable
    private fun PageDefaultSettings() {
        val contentsManager =
            remember {
                ContentsManager(this@SetupWizardActivity).also { it.syncContents() }
            }
        val installedRuntimes =
            remember(advancedInstalledSet.toList()) {
                val wineProfiles =
                    contentsManager
                        .getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE)
                        .orEmpty()
                        .filter { it.isInstalled }
                val protonProfiles =
                    contentsManager
                        .getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON)
                        .orEmpty()
                        .filter { it.isInstalled }
                (wineProfiles + protonProfiles)
            }

        if (installedRuntimes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.setup_wizard_no_runtime_installed),
                    color = Color(0xFF8B949E),
                    fontFamily = InterFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.setup_wizard_no_runtime_hint),
                    color = Color(0xFF6B7580),
                    fontFamily = InterFont,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(installedRuntimes) { profile ->
                        RuntimeContainerCard(profile)
                    }
                }
            }
        }
    }

    @Composable
    private fun RuntimeContainerCard(profile: ContentProfile) {
        val entryName = ContentsManager.getEntryName(profile)
        val displayName = runtimeDisplayLabel(profile)
        val isArm64 = profile.verName.contains("arm64ec", ignoreCase = true)
        val archLabel = if (isArm64) "ARM64EC" else "x86-64"

        val containerManager = remember { ContainerManager(this@SetupWizardActivity) }
        var existingContainer by remember {
            mutableStateOf(
                containerManager.containers.firstOrNull { it.wineVersion == entryName },
            )
        }
        val creating = creatingContainer.value

        val hasContainer = existingContainer != null
        val bgColor = Color(0xFF19212C)
        val outlineColor = if (hasContainer) Color(0xFF23436F) else Color(0xFF2A3443)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(bgColor, RoundedCornerShape(12.dp))
                    .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .background(
                                    if (hasContainer) Color(0xFF3B82F6) else Color(0xFF4A5260),
                                    RoundedCornerShape(3.dp),
                                ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = archLabel,
                        color = if (hasContainer) Color(0xFF3B82F6) else Color(0xFF8B949E),
                        fontFamily = InterFont,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = displayName,
                    color = Color(0xFFE6EDF3),
                    fontFamily = InterFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (existingContainer == null) {
                Button(
                    onClick = {
                        if (creating) return@Button
                        creatingContainer.value = true
                        lifecycleScope.launch {
                            wizardError.value = null
                            val container =
                                withContext(Dispatchers.IO) {
                                    try {
                                        val c = ensureContainerForProfile(profile, displayName)
                                        if (isArm64) {
                                            saveDefaultArm64ContainerId(this@SetupWizardActivity, c.id)
                                        } else {
                                            saveDefaultX86ContainerId(this@SetupWizardActivity, c.id)
                                        }
                                        c
                                    } catch (e: Exception) {
                                        wizardError.value = "Container creation failed: ${e.message}"
                                        null
                                    }
                                }
                            existingContainer = container
                            creatingContainer.value = false
                            refreshAdvancedInstalledSet()
                            refreshWizardState()
                        }
                    },
                    enabled = !creating && transferState.value == null,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF57CBDE),
                            contentColor = Color(0xFF111822),
                            disabledContainerColor = Color(0xFF222D3D),
                            disabledContentColor = Color(0xFF4A5260),
                        ),
                ) {
                    if (creating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFF111822),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text =
                            stringResource(
                                if (creating) R.string.setup_wizard_creating_container else R.string.setup_wizard_create_container,
                            ),
                        fontFamily = InterFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    )
                }
            } else {
                Button(
                    onClick = {
                        val id = existingContainer!!.id
                        val type = if (isArm64) "arm64" else "x86"
                        openContainerDefaultSettings(id, type)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF223140),
                            contentColor = Color(0xFFB8C5D1),
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.setup_wizard_default_settings),
                        fontFamily = InterFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }

    @Composable
    private fun WizardActionCard(
        modifier: Modifier = Modifier,
        title: String,
        subtitle: String,
        completed: Boolean,
        buttonLabel: String,
        onClick: () -> Unit,
        enabled: Boolean = true,
        progress: Float? = null,
    ) {
        val borderColor =
            when {
                completed -> Color(0xFF23436F)
                progress != null -> Color(0xFF57CBDE)
                else -> Color(0xFF222D3D)
            }
        Column(
            modifier =
                modifier
                    .background(Color(0xFF182030), RoundedCornerShape(12.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            // Status chip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(
                                when {
                                    completed -> Color(0xFF3B82F6)
                                    progress != null -> Color(0xFF57CBDE)
                                    else -> Color(0xFF4A5260)
                                },
                                RoundedCornerShape(3.dp),
                            ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = subtitle.uppercase(),
                    color = if (completed) Color(0xFF3B82F6) else Color(0xFF8B949E),
                    fontFamily = InterFont,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                color = Color(0xFFE6EDF3),
                fontFamily = InterFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(durationMillis = 400, easing = LinearEasing),
                    label = "cardProgress",
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                    color = Color(0xFF57CBDE),
                    trackColor = Color(0xFF222D3D),
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onClick,
                enabled = enabled && !completed,
                shape = RoundedCornerShape(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (completed) Color(0xFF1C2E49) else Color(0xFF57CBDE),
                        contentColor = if (completed) Color(0xFF3B82F6) else Color(0xFF111822),
                        disabledContainerColor =
                            when {
                                completed -> Color(0xFF1C2E49)
                                progress != null -> Color(0xFF1C2E49)
                                else -> Color(0xFF222D3D)
                            },
                        disabledContentColor =
                            when {
                                completed -> Color(0xFF3B82F6)
                                progress != null -> Color(0xFFE6EDF3)
                                else -> Color(0xFF4A5260)
                            },
                    ),
            ) {
                Text(
                    text = buttonLabel,
                    fontFamily = InterFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun contentVersionIdentifier(profile: ContentProfile): String = ContentsManager.getEntryName(profile).substringAfter('-')

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
    val prefix =
        when (profile.type) {
            ContentProfile.ContentType.CONTENT_TYPE_WINE -> "Wine"
            ContentProfile.ContentType.CONTENT_TYPE_PROTON -> "Proton"
            else -> profile.type.toString()
        }
    val version =
        Regex("(?i)(?:wine|proton)-([0-9]+(?:\\.[0-9]+)?)")
            .find(profile.verName)
            ?.groupValues
            ?.getOrNull(1)
            ?: profile.verName
    return "$prefix $version"
}
