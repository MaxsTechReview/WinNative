package com.winlator.cmod

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.OnExtractFileListener
import com.winlator.cmod.core.TarCompressorUtils
import com.winlator.cmod.core.TarCompressorUtils.Type
import com.winlator.cmod.xenvironment.ImageFs
import com.winlator.cmod.xenvironment.ImageFsInstaller
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class SetupWizardActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "winnative_setup"
        private const val KEY_SETUP_COMPLETE = "setup_complete"

        fun isSetupComplete(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_SETUP_COMPLETE, false)
        }

        fun markSetupComplete(context: Context) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
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

    private val storageGranted = mutableStateOf(false)
    private val notifGranted = mutableStateOf(false)
    private val notifDenied = mutableStateOf(false)
    private val installing = mutableStateOf(false)
    private val installProgress = mutableIntStateOf(0)
    private val installDone = mutableStateOf(false)
    private val installError = mutableStateOf<String?>(null)

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        storageGranted.value = hasStoragePermission()
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> 
        notifGranted.value = granted 
        if (!granted) {
            notifDenied.value = true
            if (Build.VERSION.SDK_INT >= 33 && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                openNotificationSettings()
            }
        } else {
            notifDenied.value = false
        }
    }

    private val legacyStoragePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        storageGranted.value = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true || 
                               permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Skip wizard if already complete and ImageFS is valid
        if (isSetupComplete(this) && ImageFs.find(this).isValid) {
            launchApp()
            return
        }

        // Check current permission state silently without triggering popups
        storageGranted.value = hasStoragePermission()
        notifGranted.value = hasNotificationPermissionSilently()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SetupScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions in case user granted them via Settings and returned
        storageGranted.value = hasStoragePermission()
        val isNotifGranted = hasNotificationPermissionSilently()
        notifGranted.value = isNotifGranted
        if (isNotifGranted) {
            notifDenied.value = false
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasNotificationPermissionSilently(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    @Composable
    private fun SetupScreen() {
        val storage by storageGranted
        val notif by notifGranted
        val notifIsDenied by notifDenied
        val isInstalling by installing
        val progress by installProgress
        val done by installDone
        val error by installError

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.35f)) // Extra room on top
                
                Text(
                    "Welcome to WinNative",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont,
                    color = Color(0xFFE6EDF3),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "A few quick things before we get started.",
                    fontSize = 15.sp,
                    fontFamily = InterFont,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.weight(0.75f)) 

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PermissionRow(
                        label = "File Access",
                        granted = storage,
                        required = true,
                        onRequest = { requestFileAccess() }
                    )
                    PermissionRow(
                        label = "Notifications",
                        granted = notif,
                        required = false,
                        denied = notifIsDenied,
                        onRequest = { requestNotifications() }
                    )
                }

                Spacer(Modifier.height(24.dp))

                if (error != null) {
                    Text(
                        text = error!!, 
                        color = Color(0xFFFF6B6B), 
                        fontSize = 14.sp, 
                        fontFamily = InterFont,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Spacer(Modifier.weight(0.8f))

                // Install progress or Finish button
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (isInstalling || done) {
                        val animatedProgress by animateFloatAsState(
                            targetValue = progress / 100f,
                            animationSpec = tween(durationMillis = 500),
                            label = "installProgressAnim"
                        )
                        
                        var dotCount by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(500)
                                dotCount = (dotCount + 1) % 4
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom // Push to bottom of box
                        ) {
                            val dots = ".".repeat(dotCount)
                            // Use a fixed width box or pad string to prevent layout shifting
                            val paddedDots = dots.padEnd(3, '\u00A0') // using non-breaking space for actual width in rendering
                            Text(
                                text = "Installing system files$paddedDots", 
                                color = Color(0xFF8B949E), 
                                fontSize = 14.sp, 
                                fontFamily = InterFont
                            )
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(8.dp),
                                color = Color(0xFF57CBDE),
                                trackColor = Color(0xFF21262D),
                                strokeCap = StrokeCap.Round
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "$progress%", 
                                color = Color(0xFF57CBDE), 
                                fontSize = 13.sp, 
                                fontWeight = FontWeight.SemiBold, 
                                fontFamily = SyncopateFont
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (!isInstalling) {
                                    finishSetup()
                                }
                            },
                            enabled = storage && !isInstalling,
                            modifier = Modifier.width(300.dp).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF238636),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF30363D),
                                disabledContentColor = Color(0xFF8B949E)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 2.dp,
                                disabledElevation = 0.dp
                            )
                        ) {
                            Text(
                                text = if (done) "Launch App" else "Finish Setup",
                                fontSize = 16.sp,
                                fontFamily = InterFont,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionRow(
        label: String,
        granted: Boolean,
        required: Boolean,
        denied: Boolean = false,
        onRequest: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .defaultMinSize(minHeight = 64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontFamily = InterFont,
                    color = Color(0xFFE6EDF3),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (required) "Required" else "Optional",
                    fontFamily = InterFont,
                    color = Color(0xFF8B949E),
                    fontSize = 13.sp
                )
            }
            if (granted) {
                Surface(
                    color = Color(0xFF1F2922),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Granted",
                            color = Color(0xFF3FB950),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFont
                        )
                    }
                }
            } else if (denied) {
                Button(
                    onClick = onRequest,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3D2314),
                        contentColor = Color(0xFFFF9800)
                    )
                ) {
                    Text("Denied", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFont)
                }
            } else {
                Button(
                    onClick = onRequest,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF57CBDE),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Grant", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFont)
                }
            }
        }
    }

    private fun requestFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires taking the user to settings for MANAGE_EXTERNAL_STORAGE.
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            }
        } else {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val hasRequestedOnce = prefs.getBoolean("storage_requested_once", false)
            val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)

            if (hasRequestedOnce && !shouldShowRationale) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            } else {
                prefs.edit().putBoolean("storage_requested_once", true).apply()
                legacyStoragePermLauncher.launch(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
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
            // For older Android versions or apps targeting older APIs, the system
            // prompt doesn't work the same way. The only way to enable is via Settings.
            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun finishSetup() {
        val imageFs = ImageFs.find(this)
        if (imageFs.isValid && imageFs.version >= ImageFsInstaller.LATEST_VERSION.toInt()) {
            markSetupComplete(this)
            launchApp()
            return
        }

        installing.value = true
        installError.value = null
        val rootDir = imageFs.rootDir

        Executors.newSingleThreadExecutor().execute {
            try {
                // Clear and recreate root dir (matches original installFromAssets)
                clearRootDir(rootDir)

                val compressionRatio = 22
                
                // Calculate total content length including imagefs and wine versions
                var contentLength = 0L
                val assetSize = FileUtils.getSize(this, "imagefs.txz")
                contentLength += if (assetSize > 0) {
                    (assetSize * (100.0f / compressionRatio)).toLong()
                } else {
                    800_000_000L // ~800MB estimated uncompressed
                }
                
                try {
                    val versions = resources.getStringArray(R.array.wine_entries)
                    for (version in versions) {
                        val vAssetSize = FileUtils.getSize(this, "$version.txz")
                        contentLength += if (vAssetSize > 0) {
                            (vAssetSize * (100.0f / compressionRatio)).toLong()
                        } else {
                            100_000_000L // fallback estimate
                        }
                    }
                } catch (_: Exception) {}

                val totalSize = AtomicLong()

                val listener = OnExtractFileListener { file, size ->
                    if (size > 0) {
                        val total = totalSize.addAndGet(size)
                        val pct = ((total.toFloat() / contentLength) * 100).toInt().coerceIn(0, 100)
                        runOnUiThread { installProgress.intValue = pct }
                    }
                    file
                }

                var success = TarCompressorUtils.extract(
                    Type.XZ,
                    this, "imagefs.txz", rootDir, listener
                )

                if (success) {
                    // Install wine from assets
                    try {
                        val versions = resources.getStringArray(R.array.wine_entries)
                        for (version in versions) {
                            val outFile = File(rootDir, "/opt/$version")
                            outFile.mkdirs()
                            TarCompressorUtils.extract(
                                Type.XZ,
                                this, "$version.txz", outFile, listener
                            )
                        }
                    } catch (_: Exception) {}

                    // Install drivers from assets
                    try {
                        ImageFsInstaller.installDriversFromAssets(this as? MainActivity)
                    } catch (_: Exception) {}

                    imageFs.createImgVersionFile(ImageFsInstaller.LATEST_VERSION.toInt())
                    runOnUiThread {
                        installProgress.intValue = 100
                        installDone.value = true
                        installing.value = false
                        markSetupComplete(this)
                        // Add a slight delay so the user registers the 100% state before fading out
                        Handler(Looper.getMainLooper()).postDelayed({
                            launchApp()
                        }, 500)
                    }
                } else {
                    runOnUiThread {
                        installing.value = false
                        installError.value = "Extraction failed. Please check available storage and try again."
                    }
                }
            } catch (e: Exception) {
                val msg = e.stackTraceToString().take(200)
                runOnUiThread {
                    installing.value = false
                    installError.value = "Error: ${e.message}\n$msg"
                }
            }
        }
    }

    /** Matches ImageFsInstaller.clearRootDir logic */
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
}
