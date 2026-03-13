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
    private static int pid = -1;
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

    private void extractBox64Files() {
        ImageFs imageFs = environment.getImageFs();
        Context context = environment.getContext();

        // Fallback to default if the shared preference is not set or is empty
        String box64Version = container.getBox64Version();
        if (box64Version == null || box64Version.isEmpty()) box64Version = DefaultVersion.BOX64;

        if (shortcut != null)
            box64Version = shortcut.getExtra("box64Version", box64Version);

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
            checkDependencies();
            pid = execGuestProgram();
        }
    }


    private String checkDependencies() {
        String curlPath = environment.getImageFs().getRootDir().getPath() + "/usr/lib/libXau.so";
        String lddCommand = "ldd " + curlPath;

        StringBuilder output = new StringBuilder("Checking Curl dependencies...\n");

        try {
            java.lang.Process process = Runtime.getRuntime().exec(lddCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errorReader.readLine()) != null) {
                output.append(line).append("\n");
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
        boolean enableEvshim = preferences.getBoolean("enable_evshim", false);
        if (!enableEvshim && this.envVars != null) {
            String evshimFlag = this.envVars.get("WINNATIVE_ENABLE_EVSHIM");
            enableEvshim = "1".equals(evshimFlag) || "true".equalsIgnoreCase(evshimFlag) || "yes".equalsIgnoreCase(evshimFlag);
        }

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
        } else {
            Log.d("GuestProgramLauncher", "EVSHIM disabled for compatibility mode");
        }

        addBox64EnvVars(envVars, enableBox64Logs);
        envVars.putAll(FEXCorePresetManager.getEnvVars(context, fexcorePreset));

        String renderer = GPUInformation.getRendererSafe(null, null);

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
        envVars.put("LD_LIBRARY_PATH", rootDir.getPath() + "/usr/lib" + ":" + "/system/lib64" + (wineInfo.isArm64EC() ? ":" + context.getApplicationInfo().nativeLibraryDir : ""));
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
        
        // Check for specific shared memory libraries
        if ((new File(imageFs.getLibDir(), "libandroid-sysvshm.so")).exists()){
            ld_preload = imageFs.getLibDir() + "/libandroid-sysvshm.so";
        }

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

        if (this.envVars.has("MANGOHUD")) {
            this.envVars.remove("MANGOHUD");
        }

        if (this.envVars.has("MANGOHUD_CONFIG")) {
            this.envVars.remove("MANGOHUD_CONFIG");
        }
        
        // Merge any additional environment variables from external sources
        if (this.envVars != null) {
            envVars.putAll(this.envVars);
        }

        String emulator = container.getEmulator();
        String emulator64 = container.getEmulator64();
        if (shortcut != null) {
            emulator = shortcut.getExtra("emulator", container.getEmulator());
            emulator64 = shortcut.getExtra("emulator64", container.getEmulator64());
        }

        // Force correct emulator based on architecture
        if (wineInfo.isArm64EC()) {
            // Arm64EC MUST use FEXCore
            emulator = "FEXCore";
            emulator64 = "FEXCore";
            Log.d("GuestProgramLauncherComponent", "Arm64EC detected: forcing FEXCore for both emulators");
        } else {
            // x86_64 MUST use Box64
            emulator = "Box64";
            emulator64 = "Box64";
            Log.d("GuestProgramLauncherComponent", "x86_64 detected: forcing Box64 for both emulators");
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
        }
        if (wineInfo.isArm64EC()) {
            // HODLL is mandatory for Arm64EC Wine to translate x86/x64 guest code.
            // We set both libwow64fex.dll (32-bit) and libarm64ecfex.dll (64-bit)
            // to support mixed architecture process trees (e.g. 32-bit launcher -> 64-bit game).
            envVars.put("HODLL", "libwow64fex.dll:libarm64ecfex.dll");

            command = winePath + "/" + guestExecutable;
        } else {
            // x86_64 containers use Box64 binary translation directly
            command = imageFs.getBinDir() + "/box64 " + guestExecutable;
        }

        // **Maybe remove this: Set execute permissions for box64 if necessary (Glibc/Proot artifact)
        File box64File = new File(rootDir, "/usr/bin/box64");
        if (box64File.exists()) {
            FileUtils.chmod(box64File, 0755);
        }

        Log.d("GuestProgramLauncherComponent", "=== FINAL LAUNCH COMMAND ===");
        Log.d("GuestProgramLauncherComponent", "Command: " + command);
        Log.d("GuestProgramLauncherComponent", "Working dir: " + (workingDir != null ? workingDir.getAbsolutePath() : rootDir.getAbsolutePath()));

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
}
