package com.winlator.cmod.xenvironment.components;

import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Process;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.box64.Box64Preset;
import com.winlator.cmod.box64.Box64PresetManager;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.fexcore.FEXCoreManager;
import com.winlator.cmod.fexcore.FEXCorePreset;
import com.winlator.cmod.fexcore.FEXCorePresetManager;
import com.winlator.cmod.xconnector.UnixSocketConfig;
import com.winlator.cmod.xenvironment.EnvironmentComponent;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

public class GuestProgramLauncherComponent extends EnvironmentComponent {
    private String guestExecutable;
    private int pid = -1;
    private String[] bindingPaths;
    private EnvVars envVars;
    private WineInfo wineInfo;
    private String box64Preset = Box64Preset.COMPATIBILITY;
    private String fexcorePreset = FEXCorePreset.INTERMEDIATE;
    private Callback<Integer> terminationCallback;
    private static final Object lock = new Object();
    private final ContentsManager contentsManager;
    private final ContentProfile wineProfile;
    private Container container;
    private final Shortcut shortcut;
    private File workingDir;
    private String steamType = Container.STEAM_TYPE_NORMAL;
    private Runnable preUnpackCallback;

    public static File ensureImageFsNativeLibrary(Context context, ImageFs imageFs, String libraryName) {
        File destFile = new File(imageFs.getLibDir(), libraryName);
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        File sourceFile = new File(nativeLibDir, libraryName);

        if (sourceFile.exists() && (!destFile.exists() || destFile.length() != sourceFile.length())) {
            Log.d("GuestLauncher", "Copying " + libraryName + " from nativeLibDir to imagefs (dest exists=" + destFile.exists() + ")");
            try {
                FileUtils.copy(sourceFile, destFile);
                Log.d("GuestLauncher", "Successfully copied " + libraryName + " to " + destFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e("GuestLauncher", "Failed to copy " + libraryName, e);
            }
        } else if (!destFile.exists()) {
            Log.d("GuestLauncher", "Extracting " + libraryName + " from APK (not found in nativeLibDir or imagefs)");
            try (java.util.zip.ZipFile apk = new java.util.zip.ZipFile(context.getApplicationInfo().sourceDir)) {
                String abi = android.os.Build.SUPPORTED_ABIS[0];
                String entryName = "lib/" + abi + "/" + libraryName;
                java.util.zip.ZipEntry entry = apk.getEntry(entryName);
                if (entry != null) {
                    try (InputStream is = apk.getInputStream(entry);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                        }
                    }
                    destFile.setExecutable(true, false);
                    Log.d("GuestLauncher", "Successfully extracted " + libraryName + " from APK to " + destFile.getAbsolutePath());
                } else {
                    Log.w("GuestLauncher", libraryName + " not found in APK at " + entryName);
                }
            } catch (Exception e) {
                Log.e("GuestLauncher", "Failed to extract " + libraryName, e);
            }
        } else {
            Log.d("GuestLauncher", libraryName + " already exists at " + destFile.getAbsolutePath() + " (size=" + destFile.length() + ")");
        }

        boolean result = destFile.exists();
        if (!result) {
            Log.e("GuestLauncher", libraryName + " is NOT available after ensure - this will cause issues!");
        }
        return result ? destFile : null;
    }

    public void setWorkingDir(File workingDir) {
        this.workingDir = workingDir;
    }

    public void setWineInfo(WineInfo wineInfo) {
        this.wineInfo = wineInfo;
    }
    public WineInfo getWineInfo() {
        return this.wineInfo;
    }

    public Container getContainer() { return this.container; }
    public void setContainer(Container container) { this.container = container; }

    public String getSteamType() { return steamType; }
    public void setSteamType(String steamType) {
        if (steamType == null) {
            this.steamType = Container.STEAM_TYPE_NORMAL;
            return;
        }
        String normalized = steamType.toLowerCase();
        switch (normalized) {
            case Container.STEAM_TYPE_LIGHT:
                this.steamType = Container.STEAM_TYPE_LIGHT;
                break;
            case Container.STEAM_TYPE_ULTRALIGHT:
                this.steamType = Container.STEAM_TYPE_ULTRALIGHT;
                break;
            default:
                this.steamType = Container.STEAM_TYPE_NORMAL;
        }
    }

    public String execShellCommand(String command) {
        return execShellCommand(command, true);
    }

    public String execShellCommand(String command, boolean includeStderr) {
        if (environment == null) return "";

        Context context = environment.getContext();
        ImageFs imageFs = ImageFs.find(context);
        File rootDir = imageFs.getRootDir();
        StringBuilder output = new StringBuilder();
        EnvVars envVars = new EnvVars();
        envVars.put("HOME", imageFs.home_path);
        envVars.put("USER", ImageFs.USER);
        envVars.put("TMPDIR", imageFs.getRootDir().getPath() + "/tmp");
        envVars.put("DISPLAY", ":0");

        String winePath = wineProfile == null ? imageFs.getWinePath() + "/bin"
                : ContentsManager.getSourceFile(context, wineProfile, wineProfile.wineBinPath).getAbsolutePath();
        envVars.put("PATH", winePath + ":" +
                imageFs.getRootDir().getPath() + "/usr/bin:" +
                imageFs.getRootDir().getPath() + "/usr/local/bin");
        envVars.put("LD_LIBRARY_PATH", imageFs.getRootDir().getPath() + "/usr/lib");
        envVars.put("BOX64_LD_LIBRARY_PATH", imageFs.getRootDir().getPath() + "/usr/lib/x86_64-linux-gnu");
        envVars.put("ANDROID_SYSVSHM_SERVER", imageFs.getRootDir().getPath() + UnixSocketConfig.SYSVSHM_SERVER_PATH);
        envVars.put("FONTCONFIG_PATH", imageFs.getRootDir().getPath() + "/usr/etc/fonts");

        File libDir = imageFs.getLibDir();
        File sysvshm64 = ensureImageFsNativeLibrary(context, imageFs, "libandroid-sysvshm.so");
        File libredirect64 = new File(libDir, "libredirect.so");
        Log.d("GuestLauncher", "execShellCommand LD_PRELOAD setup: sysvshm=" + (sysvshm64 != null) + " libredirect=" + libredirect64.exists());
        if ((sysvshm64 != null && sysvshm64.exists()) || libredirect64.exists()) {
            StringBuilder ldPreload = new StringBuilder();
            if (libredirect64.exists()) ldPreload.append(libredirect64.getPath());
            if (sysvshm64 != null && sysvshm64.exists()) {
                if (ldPreload.length() > 0) ldPreload.append(" ");
                ldPreload.append(sysvshm64.getPath());
            }
            envVars.put("LD_PRELOAD", ldPreload.toString());
            Log.d("GuestLauncher", "execShellCommand LD_PRELOAD=" + ldPreload.toString());
        }
        envVars.put("WINEESYNC_WINLATOR", "1");
        mergeExternalEnvVars(envVars, envVars.get("LD_PRELOAD"), envVars.get("FAKE_EVDEV_DIR"));

        // box64 may be at /usr/bin or /usr/local/bin depending on installation
        String box64Path = rootDir.getPath() + "/usr/bin/box64";
        if (!new File(box64Path).exists()) {
            box64Path = rootDir.getPath() + "/usr/local/bin/box64";
        }
        String finalCommand = box64Path + " " + command;
        try {
            Log.d("GuestProgramLauncherComponent", "Shell command is " + finalCommand);
            java.lang.Process process = Runtime.getRuntime().exec(
                    finalCommand,
                    envVars.toStringArray(),
                    workingDir != null ? workingDir : imageFs.getRootDir()
            );
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                if (includeStderr) {
                    while ((line = errorReader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            output.append("Error: ").append(e.getMessage());
        }

        return output.toString().trim();
    }

    private String resolveInstalledRuntimeVersion(String currentVersion, ContentProfile.ContentType type) {
        if (currentVersion != null && !currentVersion.isEmpty()) {
            ContentProfile currentProfile = contentsManager.getProfileByEntryName(type.toString() + "-" + currentVersion);
            if (currentProfile != null && currentProfile.isInstalled) {
                return currentVersion;
            }
        }

        ContentProfile preferredProfile = null;
        for (ContentProfile profile : contentsManager.getProfiles(type)) {
            if (!profile.isInstalled) continue;

            if (preferredProfile == null ||
                    profile.verCode > preferredProfile.verCode ||
                    (profile.verCode == preferredProfile.verCode &&
                            profile.verName.compareToIgnoreCase(preferredProfile.verName) > 0)) {
                preferredProfile = profile;
            }
        }

        if (preferredProfile != null) {
            String entryName = ContentsManager.getEntryName(preferredProfile);
            int firstDashIndex = entryName.indexOf('-');
            return firstDashIndex >= 0 ? entryName.substring(firstDashIndex + 1) : preferredProfile.verName;
        }

        return currentVersion;
    }

    private void extractBox64Files() {
        ImageFs imageFs = environment.getImageFs();
        Context context = environment.getContext();

        // Fallback to default if the shared preference is not set or is empty
        String box64Version = container.getBox64Version();
        if (box64Version == null || box64Version.isEmpty()) box64Version = DefaultVersion.BOX64;

        if (shortcut != null)
            box64Version = shortcut.getExtra("box64Version", box64Version);

        box64Version = resolveInstalledRuntimeVersion(box64Version, ContentProfile.ContentType.CONTENT_TYPE_BOX64);

        Log.d("GuestProgramLauncherComponent", "box64Version: " + box64Version);

        File rootDir = imageFs.getRootDir();
        boolean box64Missing = !new File(rootDir, "/usr/bin/box64").exists();

        if (box64Missing || !box64Version.equals(container.getExtra("box64Version"))) {
            ContentProfile profile = contentsManager.getProfileByEntryName("box64-" + box64Version);
            if (profile != null)
                contentsManager.applyContent(profile);
            else
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "box64/box64-" + box64Version + ".tzst", rootDir);
            container.putExtra("box64Version", box64Version);
            container.saveData();
        }

        // Set execute permissions for box64 just in case
        File box64File = new File(rootDir, "/usr/bin/box64");
        if (box64File.exists()) {
            FileUtils.chmod(box64File, 0755);
        }
    }

    private void extractEmulatorsDlls() {
        Context context = environment.getContext();
        File rootDir = environment.getImageFs().getRootDir();
        File system32dir = new File(rootDir + "/home/xuser/.wine/drive_c/windows/system32");
        boolean containerDataChanged = false;

        String wowbox64Version = container.getBox64Version();
        String fexcoreVersion = container.getFEXCoreVersion();

        // Null-safe fallback to defaults (handles legacy containers without these fields)
        if (wowbox64Version == null || wowbox64Version.isEmpty()) wowbox64Version = DefaultVersion.BOX64;
        if (fexcoreVersion == null || fexcoreVersion.isEmpty()) fexcoreVersion = DefaultVersion.FEXCORE;

        if (shortcut != null) {
            wowbox64Version = shortcut.getExtra("box64Version", wowbox64Version);
            fexcoreVersion = shortcut.getExtra("fexcoreVersion", fexcoreVersion);
        }

        wowbox64Version = resolveInstalledRuntimeVersion(wowbox64Version, ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64);
        fexcoreVersion = resolveInstalledRuntimeVersion(fexcoreVersion, ContentProfile.ContentType.CONTENT_TYPE_FEXCORE);

        Log.d("GuestProgramLauncherComponent", "box64Version in use: " + wowbox64Version);
        Log.d("GuestProgramLauncherComponent", "fexcoreVersion in use: " + fexcoreVersion);

        // Check if critical FEXCore DLLs actually exist on disk (they may be missing even if version matches)
        boolean fexcoreDllsMissing = !new File(system32dir, "libwow64fex.dll").exists() || !new File(system32dir, "libarm64ecfex.dll").exists();
        boolean wowbox64DllMissing = !new File(system32dir, "wowbox64.dll").exists();

        if (fexcoreDllsMissing) {
            Log.w("GuestProgramLauncherComponent", "FEXCore DLLs missing from system32 (libwow64fex.dll or libarm64ecfex.dll), forcing re-extraction");
        }
        if (wowbox64DllMissing) {
            Log.w("GuestProgramLauncherComponent", "wowbox64.dll missing from system32, forcing re-extraction");
        }

        if (wowbox64DllMissing || !wowbox64Version.equals(container.getExtra("box64Version"))) {
            ContentProfile profile = contentsManager.getProfileByEntryName("wowbox64-" + wowbox64Version);
            if (profile != null)
                contentsManager.applyContent(profile);
            else
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, environment.getContext(), "wowbox64/wowbox64-" + wowbox64Version + ".tzst", system32dir);
            container.putExtra("box64Version", wowbox64Version);
            containerDataChanged = true;
        }

        if (fexcoreDllsMissing || !fexcoreVersion.equals(container.getExtra("fexcoreVersion"))) {
            ContentProfile profile = contentsManager.getProfileByEntryName("fexcore-" + fexcoreVersion);
            if (profile != null)
                contentsManager.applyContent(profile);
            else
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, environment.getContext(), "fexcore/fexcore-" + fexcoreVersion + ".tzst", system32dir);
            container.putExtra("fexcoreVersion", fexcoreVersion);
            containerDataChanged = true;
        }
        if (containerDataChanged) container.saveData();
    }

    public GuestProgramLauncherComponent(ContentsManager contentsManager, ContentProfile wineProfile, Shortcut shortcut) {
        this.contentsManager = contentsManager;
        this.wineProfile = wineProfile;
        this.shortcut = shortcut;
    }

    @Override
    public void start() {
        synchronized (lock) {
            if (wineInfo.isArm64EC())
                extractEmulatorsDlls();
            else
                extractBox64Files();
            copyDefaultBox64RCFile();
            checkDependencies();

            // Run Steamless DRM stripping if configured (must happen after box64 is ready
            // but before the game exe is launched)
            if (preUnpackCallback != null) {
                try {
                    Log.d("GuestProgramLauncherComponent", "Running preUnpack callback (Steamless DRM stripping)");
                    preUnpackCallback.run();
                } catch (Exception e) {
                    Log.e("GuestProgramLauncherComponent", "preUnpack callback failed", e);
                }
            }

            pid = execGuestProgram();
        }
    }

    private void copyDefaultBox64RCFile() {
        Context context = environment.getContext();
        ImageFs imageFs = ImageFs.find(context);
        File rootDir = imageFs.getRootDir();
        String assetPath;
        switch (steamType) {
            case Container.STEAM_TYPE_LIGHT:
                assetPath = "box86_64/lightsteam.box64rc";
                break;
            case Container.STEAM_TYPE_ULTRALIGHT:
                assetPath = "box86_64/ultralightsteam.box64rc";
                break;
            default:
                assetPath = "box86_64/default.box64rc";
                break;
        }
        FileUtils.copy(context, assetPath, new File(rootDir, "/etc/config.box64rc"));
    }


    private String checkDependencies() {
        String curlPath = environment.getImageFs().getRootDir().getPath() + "/usr/lib/libXau.so";
        String lddCommand = "ldd " + curlPath;

        StringBuilder output = new StringBuilder("Checking Curl dependencies...\n");

        try {
            java.lang.Process process = Runtime.getRuntime().exec(lddCommand);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
        } catch (Exception e) {
            output.append("Error running ldd: ").append(e.getMessage());
        }

        Log.d("CurlDeps", output.toString()); // Log the full dependency output
        return output.toString();
    }


    @Override
    public void stop() {
        synchronized (lock) {
            if (pid != -1) {
                Process.killProcess(pid);
                pid = -1;
            }
        }
    }

    public Callback<Integer> getTerminationCallback() {
        return terminationCallback;
    }

    public void setTerminationCallback(Callback<Integer> terminationCallback) {
        this.terminationCallback = terminationCallback;
    }

    public void setPreUnpack(Runnable callback) {
        this.preUnpackCallback = callback;
    }

    public String getGuestExecutable() {
        return guestExecutable;
    }

    public void setGuestExecutable(String guestExecutable) {
        this.guestExecutable = guestExecutable;
    }

    public String[] getBindingPaths() {
        return bindingPaths;
    }

    public void setBindingPaths(String[] bindingPaths) {
        this.bindingPaths = bindingPaths;
    }

    public EnvVars getEnvVars() {
        return envVars;
    }

    public void setEnvVars(EnvVars envVars) {
        this.envVars = envVars;
    }

    public String getBox64Preset() {
        return box64Preset;
    }

    public void setBox64Preset(String box64Preset) {
        this.box64Preset = box64Preset;
    }

    public void setFEXCorePreset (String fexcorePreset) { this.fexcorePreset = fexcorePreset; }

    private static String mergePreloadValue(String baseValue, String overrideValue) {
        if (overrideValue == null || overrideValue.isEmpty()) {
            return baseValue == null ? "" : baseValue;
        }
        if (baseValue == null || baseValue.isEmpty()) {
            return overrideValue;
        }
        if (overrideValue.equals(baseValue)) {
            return baseValue;
        }
        return baseValue + ":" + overrideValue;
    }

    private void mergeExternalEnvVars(EnvVars envVars, String protectedLdPreload, String protectedFakeEvdevDir) {
        if (this.envVars == null) {
            return;
        }

        if (this.envVars.has("MANGOHUD")) {
            this.envVars.remove("MANGOHUD");
        }

        if (this.envVars.has("MANGOHUD_CONFIG")) {
            this.envVars.remove("MANGOHUD_CONFIG");
        }

        String overrideLdPreload = this.envVars.get("LD_PRELOAD");
        String overrideFakeEvdevDir = this.envVars.get("FAKE_EVDEV_DIR");

        envVars.putAll(this.envVars);

        if (protectedLdPreload != null && !protectedLdPreload.isEmpty()) {
            envVars.put("LD_PRELOAD", mergePreloadValue(protectedLdPreload, overrideLdPreload));
        }

        if (protectedFakeEvdevDir != null && !protectedFakeEvdevDir.isEmpty()) {
            envVars.put("FAKE_EVDEV_DIR", protectedFakeEvdevDir);
        } else if (overrideFakeEvdevDir != null && !overrideFakeEvdevDir.isEmpty()) {
            envVars.put("FAKE_EVDEV_DIR", overrideFakeEvdevDir);
        }
    }



    private int execGuestProgram() {
        Context context = environment.getContext();
        ImageFs imageFs = environment.getImageFs();
        File rootDir = imageFs.getRootDir();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enableBox64Logs = preferences.getBoolean("enable_box64_logs", false);
        boolean openWithAndroidBrowser = preferences.getBoolean("open_with_android_browser", false);
        boolean shareAndroidClipboard = preferences.getBoolean("share_android_clipboard", false);

        if (openWithAndroidBrowser)
            envVars.put("WINE_OPEN_WITH_ANDROID_BROWSER", "1");
        if (shareAndroidClipboard) {
            envVars.put("WINE_FROM_ANDROID_CLIPBOARD", "1");
            envVars.put("WINE_TO_ANDROID_CLIPBOARD", "1");
        }

        EnvVars envVars = new EnvVars();
        boolean enableEvshim = false;

        if (enableEvshim) {
            // --- Controller support: create shared memory files for all 4 slots ---
            // Pre-create all files to support hot-plug (controllers connected mid-game)
            final int MAX_PLAYERS = 4;
            File tmpDir = new File(rootDir, "tmp");
            tmpDir.mkdirs();
            String tmpPath = tmpDir.getAbsolutePath();
            for (int i = 0; i < MAX_PLAYERS; i++) {
                String memPath = (i == 0)
                        ? tmpPath + "/gamepad.mem"
                        : tmpPath + "/gamepad" + i + ".mem";
                File memFile = new File(memPath);
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(memFile, "rw")) {
                    raf.setLength(64);
                } catch (IOException e) {
                    Log.e("GuestProgramLauncher", "Failed to create mem file for player " + i, e);
                }
            }
            envVars.put("EVSHIM_MAX_PLAYERS", String.valueOf(MAX_PLAYERS));
            envVars.put("EVSHIM_DATA_PATH", tmpPath);
            envVars.put("EVSHIM_WIN_PATH", "Z:\\tmp");
        } else {
            Log.d("GuestProgramLauncher", "EVSHIM disabled for compatibility mode");
        }

        addBox64EnvVars(envVars, enableBox64Logs);
        envVars.putAll(FEXCorePresetManager.getEnvVars(context, fexcorePreset));

        String renderer = GPUInformation.getRenderer(null, null);

        if (renderer.contains("Mali"))
            envVars.put("BOX64_MMAP32", "0");

        if (envVars.get("BOX64_MMAP32").equals("1") && !wineInfo.isArm64EC()) {
            Log.d("GuestProgramLauncherComponent", "Disabling map memory placed");
            envVars.put("WRAPPER_DISABLE_PLACED", "1");
        }

        // Setting up essential environment variables for Wine
        envVars.put("HOME", imageFs.home_path);
        envVars.put("USER", ImageFs.USER);
        envVars.put("TMPDIR", rootDir.getPath() + "/usr/tmp");
        envVars.put("XDG_DATA_DIRS", rootDir.getPath() + "/usr/share");
        envVars.put("LD_LIBRARY_PATH", rootDir.getPath() + "/usr/lib" + ":" + "/system/lib64");
        envVars.put("XDG_CONFIG_DIRS", rootDir.getPath() + "/usr/etc/xdg");
        envVars.put("GST_PLUGIN_PATH", rootDir.getPath() + "/usr/lib/gstreamer-1.0");
        envVars.put("FONTCONFIG_PATH", rootDir.getPath() + "/usr/etc/fonts");
        envVars.put("VK_LAYER_PATH", rootDir.getPath() + "/usr/share/vulkan/implicit_layer.d" + ":" + rootDir.getPath() + "/usr/share/vulkan/explicit_layer.d");
        envVars.put("WRAPPER_LAYER_PATH", rootDir.getPath() + "/usr/lib");
        envVars.put("WRAPPER_CACHE_PATH", rootDir.getPath() + "/usr/var/cache");
        envVars.put("WINE_NO_DUPLICATE_EXPLORER", "1");
        envVars.put("PREFIX", rootDir.getPath() + "/usr");
        envVars.put("DISPLAY", ":0");
        envVars.put("WINE_DISABLE_FULLSCREEN_HACK", "1");
        envVars.put("ENABLE_UTIL_LAYER", "1");
        envVars.put("GST_PLUGIN_FEATURE_RANK", "ximagesink:3000");
        envVars.put("ALSA_CONFIG_PATH", rootDir.getPath() + "/usr/share/alsa/alsa.conf" + ":" + rootDir.getPath() + "/usr/etc/alsa/conf.d/android_aserver.conf");
        envVars.put("ALSA_PLUGIN_DIR", rootDir.getPath() + "/usr/lib/alsa-lib");
        envVars.put("OPENSSL_CONF", rootDir.getPath() + "/usr/etc/tls/openssl.cnf");
        envVars.put("SSL_CERT_FILE", rootDir.getPath() + "/usr/etc/tls/cert.pem");
        envVars.put("SSL_CERT_DIR", rootDir.getPath() + "/usr/etc/tls/certs");
        envVars.put("WINE_X11FORCEGLX", "1");
        envVars.put("WINE_GST_NO_GL", "1");
        envVars.put("SteamGameId", "0");
        envVars.put("PROTON_AUDIO_CONVERT", "0");
        envVars.put("PROTON_VIDEO_CONVERT", "0");
        envVars.put("PROTON_DEMUX", "0");

        String winePath = imageFs.getWinePath() + "/bin";

        Log.d("GuestProgramLauncherComponent", "WinePath is " + winePath);

        envVars.put("PATH", winePath + ":" +
                rootDir.getPath() + "/usr/bin");

        envVars.put("ANDROID_SYSVSHM_SERVER", rootDir.getPath() + UnixSocketConfig.SYSVSHM_SERVER_PATH);

        String primaryDNS = "8.8.4.4";
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Service.CONNECTIVITY_SERVICE);
        if (connectivityManager.getActiveNetwork() != null) {
            ArrayList<InetAddress> dnsServers = new ArrayList<>(connectivityManager.getLinkProperties(connectivityManager.getActiveNetwork()).getDnsServers());
            primaryDNS = dnsServers.get(0).toString().substring(1);
        }
        envVars.put("ANDROID_RESOLV_DNS", primaryDNS);
        envVars.put("WINE_NEW_NDIS", "1");
        
        String ld_preload = "";

        // Ensure shared memory library is extracted and available
        File sysvshmDest = ensureImageFsNativeLibrary(context, imageFs, "libandroid-sysvshm.so");
        if (sysvshmDest != null && sysvshmDest.exists()){
            ld_preload = sysvshmDest.getAbsolutePath();
        } else {
            Log.w("GuestProgramLauncher", "libandroid-sysvshm.so not available for LD_PRELOAD - shared memory may fail!");
        }

        File fakeinputDest = ensureImageFsNativeLibrary(context, imageFs, "libfakeinput.so");

        if (fakeinputDest != null && fakeinputDest.exists()) {
            if (!ld_preload.isEmpty()) {
                ld_preload += ":";
            }
            ld_preload += fakeinputDest.getAbsolutePath();
        } else {
            Log.w("GuestProgramLauncher", "libfakeinput.so not available for LD_PRELOAD");
        }
        Log.d("GuestProgramLauncher", "execGuestProgram LD_PRELOAD=" + ld_preload);

        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        devInputDir.mkdirs();
        File event0 = new File(devInputDir, "event0");
        if (!event0.exists()) {
            try {
                event0.createNewFile();
            } catch (Exception e) {
            }
        }
        envVars.put("FAKE_EVDEV_DIR", devInputDir.getAbsolutePath());

        if (enableEvshim) {
            // Create libSDL symlink if necessary for evshim to intercept correctly
            try {
                File sdlSource = new File(imageFs.getLibDir(), "libSDL2-2.0.so");
                File sdlSymlink = new File(imageFs.getLibDir(), "libSDL2-2.0.so.0");
                if (sdlSource.exists() && !sdlSymlink.exists()) {
                    android.system.Os.symlink(sdlSource.getAbsolutePath(), sdlSymlink.getAbsolutePath());
                }

                File sdlSourceAlt = new File(imageFs.getLibDir(), "libSDL2.so");
                if (!sdlSource.exists() && sdlSourceAlt.exists() && !sdlSymlink.exists()) {
                    android.system.Os.symlink(sdlSourceAlt.getAbsolutePath(), sdlSymlink.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e("GuestProgramLauncherComponent", "Failed to setup SDL2 symlink", e);
            }

            // Add evshim for controller support (creates virtual SDL joysticks)
            // Extract libevshim.so from APK native libs to imagefs if needed
            // (Android may not extract native libs to disk on newer versions)
            File evshimInImagefs = new File(imageFs.getLibDir(), "libevshim.so");
            String apkNativeLibDir = context.getApplicationInfo().nativeLibraryDir;
            File evshimInNativeDir = new File(apkNativeLibDir, "libevshim.so");

            if (evshimInNativeDir.exists() && (!evshimInImagefs.exists() || evshimInImagefs.length() != evshimInNativeDir.length())) {
                // Native libs are extracted to disk - copy to imagefs
                FileUtils.copy(evshimInNativeDir, evshimInImagefs);
                Log.d("GuestProgramLauncher", "Copied evshim from nativeLibDir to imagefs");
            } else if (!evshimInImagefs.exists()) {
                // Native libs NOT extracted (compressed in APK) - extract from APK
                try {
                    String abi = android.os.Build.SUPPORTED_ABIS[0];
                    String entryName = "lib/" + abi + "/libevshim.so";
                    java.util.zip.ZipFile apk = new java.util.zip.ZipFile(context.getApplicationInfo().sourceDir);
                    java.util.zip.ZipEntry entry = apk.getEntry(entryName);
                    if (entry != null) {
                        try (java.io.InputStream is = apk.getInputStream(entry);
                             java.io.FileOutputStream fos = new java.io.FileOutputStream(evshimInImagefs)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
                        }
                        evshimInImagefs.setExecutable(true, false);
                        Log.d("GuestProgramLauncher", "Extracted evshim from APK to imagefs: " + evshimInImagefs.getAbsolutePath());
                    }
                    apk.close();
                } catch (Exception e) {
                    Log.e("GuestProgramLauncher", "Failed to extract evshim from APK", e);
                }
            }

            if (evshimInImagefs.exists()) {
                ld_preload += (ld_preload.isEmpty() ? "" : ":") + evshimInImagefs.getAbsolutePath();
                Log.d("GuestProgramLauncher", "evshim added to LD_PRELOAD: " + evshimInImagefs.getAbsolutePath());
            } else {
                Log.w("GuestProgramLauncher", "libevshim.so not found anywhere!");
            }
        }

        // Winlator legacy hooks (libhook_impl.so, libfile_redirect_hook.so) break FEXCore rendering and stability.
        // Removed them for Arm64EC mode to match Bionic/Ludashi implementation.
        if (wineInfo.isArm64EC()) {
            // Do not preload libhook_impl.so or libfile_redirect_hook.so on Arm64EC.
        }

        envVars.put("LD_PRELOAD", ld_preload);

        mergeExternalEnvVars(envVars, ld_preload, devInputDir.getAbsolutePath());

        String emulator = container.getEmulator();
        String emulator64 = container.getEmulator64();
        if (shortcut != null) {
            emulator = shortcut.getExtra("emulator", container.getEmulator());
            emulator64 = shortcut.getExtra("emulator64", container.getEmulator64());
        }

        // Normalize slots to the runtime components that actually exist:
        // x86_64 → Box64 binary + wowbox64.dll; ARM64EC → libwow64fex.dll + (FEX or wowbox64).
        if (wineInfo.isArm64EC()) {
            emulator64 = "FEXCore";
            // Legacy shortcuts may have "box64" saved for 32-bit; fall back.
            if (!"fexcore".equalsIgnoreCase(emulator) && !"wowbox64".equalsIgnoreCase(emulator)) {
                emulator = "FEXCore";
            }
            Log.d("GuestProgramLauncherComponent",
                    "Arm64EC detected: emulator64=FEXCore, emulator(32-bit)=" + emulator);
        } else {
            emulator64 = "Box64";
            emulator = "Wowbox64";
            Log.d("GuestProgramLauncherComponent",
                    "x86_64 detected: emulator64=Box64, emulator(32-bit)=Wowbox64");
        }

        Log.d("GuestProgramLauncherComponent", "=== EMULATOR SELECTION ===");
        Log.d("GuestProgramLauncherComponent", "Wine arch: " + wineInfo.getArch() + " isArm64EC: " + wineInfo.isArm64EC());
        Log.d("GuestProgramLauncherComponent", "Emulator (32-bit): " + emulator);
        Log.d("GuestProgramLauncherComponent", "Emulator (64-bit): " + emulator64);

        boolean is64Bit = true;

        // Find the actual .exe file to check architecture
        File exeFile = null;
        String winPath = null;
        if (guestExecutable.contains("\"")) {
            int start = guestExecutable.indexOf("\"") + 1;
            int end = guestExecutable.indexOf("\"", start);
            if (start > 0 && end > start) winPath = guestExecutable.substring(start, end);
        } else {
            // If not quoted, take the first part before any space
            int spaceIndex = guestExecutable.indexOf(" ");
            winPath = spaceIndex != -1 ? guestExecutable.substring(0, spaceIndex) : guestExecutable;
        }

        if (winPath != null && winPath.toLowerCase().endsWith(".exe")) {
            exeFile = com.winlator.cmod.core.WineUtils.getNativePath(imageFs, winPath);
            if (exeFile != null) Log.d("GuestProgramLauncherComponent", "Detected executable for arch check: " + exeFile.getAbsolutePath());
        }

        // Determine which emulator to use for HODLL based on guest executable architecture
        String selectedEmulator = emulator;
        if (wineInfo.isArm64EC()) {
            is64Bit = (exeFile != null && com.winlator.cmod.core.PEHelper.is64Bit(exeFile)) || 
                             (guestExecutable != null && guestExecutable.contains("steamclient_loader_x64.exe"));
            
            if (is64Bit) {
                selectedEmulator = emulator64;
            }
        }

        // Construct the command without Box64 to the Wine executable
        String command = "";
        String overriddenCommand = envVars.get("GUEST_PROGRAM_LAUNCHER_COMMAND");
        if (overriddenCommand != null && !overriddenCommand.isEmpty()) {
            String[] parts = overriddenCommand.split(";");
            for (String part : parts)
                command += part + " ";
            command = command.trim();
        } else {
            if (wineInfo.isArm64EC()) {
                command = winePath + "/" + guestExecutable;
                if ("fexcore".equalsIgnoreCase(selectedEmulator))
                    envVars.put("HODLL", "libwow64fex.dll");
                else
                    envVars.put("HODLL", "wowbox64.dll");
            } else {
                command = imageFs.getBinDir() + "/box64 " + guestExecutable;
            }
        }

        // **Maybe remove this: Set execute permissions for box64 if necessary (Glibc/Proot artifact)
        File box64File = new File(rootDir, "/usr/bin/box64");
        if (box64File.exists()) {
            FileUtils.chmod(box64File, 0755);
        }

        Log.d("GuestProgramLauncherComponent", "=== FINAL LAUNCH COMMAND ===");
        Log.d("GuestProgramLauncherComponent", "Command: " + command);
        Log.d("GuestProgramLauncherComponent", "Working dir: " + (workingDir != null ? workingDir.getAbsolutePath() : rootDir.getAbsolutePath()));
        Log.d("GuestProgramLauncherComponent", "=== FINAL ENVIRONMENT (" + envVars.toStringArray().length + " vars) ===");
        for (String kv : envVars.toStringArray()) {
            Log.d("GuestProgramLauncherComponent", "env " + kv);
        }

        return ProcessHelper.exec(command, envVars.toStringArray(), workingDir != null ? workingDir : rootDir, (status) -> {
            synchronized (lock) {
                pid = -1;
            }

            if (terminationCallback != null)
                terminationCallback.call(status);
        });
    }

    private void addBox64EnvVars(EnvVars envVars, boolean enableLogs) {
        envVars.put("BOX64_NOBANNER", ProcessHelper.PRINT_DEBUG && enableLogs ? "0" : "1");
        envVars.put("BOX64_DYNAREC", "1");

        if (enableLogs) {
            envVars.put("BOX64_LOG", "1");
            envVars.put("BOX64_DYNAREC_MISSING", "1");
        }

        envVars.putAll(Box64PresetManager.getEnvVars("box64", environment.getContext(), box64Preset));
        envVars.put("BOX64_X11GLX", "1");
        envVars.put("BOX64_NORCFILES", "1");
    }

    public void suspendProcess() {
        synchronized (lock) {
            if (pid != -1) ProcessHelper.suspendProcess(pid);
        }
    }

    public void resumeProcess() {
        synchronized (lock) {
            if (pid != -1) ProcessHelper.resumeProcess(pid);
        }
    }

    private String resolveWineBinary(String wineBinDir, boolean prefer64BitWine) {
        File preferredBinary = new File(wineBinDir, prefer64BitWine ? "wine64" : "wine");
        if (preferredBinary.exists()) return preferredBinary.getAbsolutePath();

        File fallbackBinary = new File(wineBinDir, "wine");
        if (fallbackBinary.exists()) return fallbackBinary.getAbsolutePath();

        return preferredBinary.getAbsolutePath();
    }

    private String resolveWineServerBinary(String wineBinDir, boolean prefer64BitWine) {
        File preferredBinary = new File(wineBinDir, prefer64BitWine ? "wineserver64" : "wineserver");
        if (preferredBinary.exists()) return preferredBinary.getAbsolutePath();

        File fallbackBinary = new File(wineBinDir, "wineserver");
        return fallbackBinary.exists() ? fallbackBinary.getAbsolutePath() : null;
    }

    private String pinWineLoader(String command, String wineLoader) {
        if (command == null || command.isEmpty()) return command;
        if (command.equals("wine") || command.equals("wine64")) return wineLoader;
        if (command.startsWith("wine ")) return wineLoader + command.substring(4);
        if (command.startsWith("wine64 ")) return wineLoader + command.substring(6);
        return command;
    }
}
