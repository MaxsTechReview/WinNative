package com.winlator.cmod.xenvironment.components;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.box86_64.Box86_64Preset;
import com.winlator.cmod.box86_64.Box86_64PresetManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.xconnector.UnixSocketConfig;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class GlibcProgramLauncherComponent extends GuestProgramLauncherComponent {
    private String guestExecutable;
    private static int pid = -1;
    private String[] bindingPaths;
    private EnvVars envVars;
    private String box86Preset = Box86_64Preset.COMPATIBILITY;
    private String box64Preset = Box86_64Preset.COMPATIBILITY;
    private Callback<Integer> terminationCallback;
    private static final Object lock = new Object();
    private boolean wow64Mode = true;
    private final ContentsManager contentsManager;
    private final ContentProfile wineProfile;

    private final Shortcut shortcut;
    private String box64Version;



    public GlibcProgramLauncherComponent(ContentsManager contentsManager, ContentProfile wineProfile, Shortcut shortcut) {
        this.contentsManager = contentsManager;
        this.wineProfile = wineProfile;
        this.shortcut = shortcut;
    }

    @Override
    public void start() {
        synchronized (lock) {
            // Proceed with other startup tasks
            extractBox86_64Files();
            checkCurlDependencies();
            pid = execGuestProgram();
        }
    }


//    private void executeCurlInBackground() {
//        String curlPath = environment.getImageFs().getRootDir().getPath() + "/usr/bin/vk_instance_debugger";
////        String[] command = {curlPath, "--help"};
//        String[] command = {
//                "/data/user/0/com.winlator/files/imagefs/usr/lib/ld-linux-aarch64.so.1",
//                "--library-path", "/data/user/0/com.winlator/files/imagefs/usr/lib",
//                "/data/user/0/com.winlator/files/imagefs/usr/bin/vk_instance_debugger", "--help"
//        };
//
//
//        EnvVars envVars = new EnvVars();
//        envVars.put("LD_LIBRARY_PATH", environment.getImageFs().getRootDir().getPath() + "/usr/lib");
//
//        new Thread(() -> {
//            StringBuilder output = new StringBuilder("Running curl in the background...\n");
//
//            try {
//                java.lang.Process process = Runtime.getRuntime().exec(command, envVars.toStringArray(), new File(environment.getImageFs().getRootDir().getPath()));
//                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
//
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    output.append(line).append("\n");
//                }
//                while ((line = errorReader.readLine()) != null) {
//                    output.append(line).append("\n");
//                }
//
//                int exitCode = process.waitFor();
//                output.append("Curl finished with exit code: ").append(exitCode).append("\n");
//            } catch (Exception e) {
//                output.append("Error running curl: ").append(e.getMessage()).append("\n");
//            }
//
//            Log.d("CurlOutput", output.toString());
//        }).start();
//    }
//
//    public void runWithoutBox64(String programPath) {
//        String[] command = {programPath, "--help"};  // Example to run `curl --help`
//
//        EnvVars envVars = new EnvVars();
//        envVars.put("LD_LIBRARY_PATH", environment.getImageFs().getRootDir().getPath() + "/usr/lib");
//
//        new Thread(() -> {
//            StringBuilder output = new StringBuilder("Running program without Box64...\n");
//
//            try {
//                java.lang.Process process = Runtime.getRuntime().exec(command, envVars.toStringArray(), new File(environment.getImageFs().getRootDir().getPath()));
//                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
//
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    output.append(line).append("\n");
//                }
//                while ((line = errorReader.readLine()) != null) {
//                    output.append(line).append("\n");
//                }
//
//                int exitCode = process.waitFor();
//                output.append("Program finished with exit code: ").append(exitCode).append("\n");
//            } catch (Exception e) {
//                output.append("Error running program: ").append(e.getMessage()).append("\n");
//            }
//
//            Log.d("ProgramOutput", output.toString());
//        }).start();
//    }
//
//
    private String checkCurlDependencies() {
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

    public boolean isWoW64Mode() {
        return wow64Mode;
    }

    public void setWoW64Mode(boolean wow64Mode) {
        this.wow64Mode = wow64Mode;
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

    public String getBox86Preset() {
        return box86Preset;
    }

    public void setBox86Preset(String box86Preset) {
        this.box86Preset = box86Preset;
    }

    public String getBox64Preset() {
        return box64Preset;
    }

    public void setBox64Preset(String box64Preset) {
        this.box64Preset = box64Preset;
    }

//    private int execGuestProgram() {
//        Context context = environment.getContext();
//        ImageFs imageFs = environment.getImageFs();
//        File rootDir = imageFs.getRootDir();
//
//        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
//        boolean enableBox86_64Logs = preferences.getBoolean("enable_box86_64_logs", false);
//
//        EnvVars envVars = new EnvVars();
//        if (!wow64Mode) addBox86EnvVars(envVars, enableBox86_64Logs);
//        addBox64EnvVars(envVars, enableBox86_64Logs);
//        envVars.put("HOME", imageFs.home_path);
//        envVars.put("USER", ImageFs.USER);
//        envVars.put("TMPDIR", imageFs.getRootDir().getPath() + "/tmp");
//        envVars.put("DISPLAY", ":0");
//
//        String winePath = wineProfile == null ? imageFs.getWinePath() + "/bin"
//                : ContentsManager.getSourceFile(context, wineProfile, wineProfile.wineBinPath).getAbsolutePath();
//        envVars.put("PATH", winePath + ":" +
//                imageFs.getRootDir().getPath() + "/usr/bin:" +
//                imageFs.getRootDir().getPath() + "/usr/local/bin");
//
//        envVars.put("LD_LIBRARY_PATH", imageFs.getRootDir().getPath() + "/usr/lib");
//        envVars.put("BOX64_LD_LIBRARY_PATH", imageFs.getRootDir().getPath() + "/usr/lib/x86_64-linux-gnu");
//        envVars.put("ANDROID_SYSVSHM_SERVER", imageFs.getRootDir().getPath() + UnixSocketConfig.SYSVSHM_SERVER_PATH);
//        envVars.put("FONTCONFIG_PATH", imageFs.getRootDir().getPath() + "/usr/etc/fonts");
//
////        File libMangoHud = new File(imageFs.getRootDir(), "usr/lib/mangohud/libMangoHud.so");
////        if (libMangoHud.exists()) {
////            libMangoHud.setExecutable(true, false); // Set execute permissions for all users
////        }
//
//
//        if ((new File(imageFs.getGlibc64Dir(), "libandroid-sysvshm.so")).exists() ||
//                (new File(imageFs.getGlibc32Dir(), "libandroid-sysvshm.so")).exists())
//            envVars.put("LD_PRELOAD", "libandroid-sysvshm.so");
//        if (this.envVars != null) envVars.putAll(this.envVars);
//
//        String command = rootDir.getPath() + "/usr/local/bin/box64 ";
//
//        // Set execute permissions for box64 just in case
//        File box64File = new File(rootDir, "/usr/local/bin/box64");
//        if (box64File.exists()) {
//            FileUtils.chmod(box64File, 0755);
//        }
//
//        command += guestExecutable;
//
//        Log.d("LD_LIBRARY_PATH", envVars.get("LD_LIBRARY_PATH"));
//
//        return ProcessHelper.exec(command, envVars.toStringArray(), rootDir, (status) -> {
//            synchronized (lock) {
//                pid = -1;
//            }
//            if (terminationCallback != null) terminationCallback.call(status);
//        });
//    }

    private int execGuestProgram() {
        Context context = environment.getContext();
        ImageFs imageFs = environment.getImageFs();
        File rootDir = imageFs.getRootDir();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enableBox86_64Logs = preferences.getBoolean("enable_box86_64_logs", false);

        EnvVars envVars = new EnvVars();

        checkXServerConnectionUnixSocket();

        // Add the Box86 and Box64 environment variables depending on the mode
        if (!wow64Mode) {
            addBox86EnvVars(envVars, enableBox86_64Logs);
        }
        addBox64EnvVars(envVars, enableBox86_64Logs);

        // Setting up essential environment variables for Wine
        envVars.put("HOME", imageFs.home_path);
        envVars.put("USER", ImageFs.USER);
        envVars.put("TMPDIR", rootDir.getPath() + "/usr/tmp");
        envVars.put("DISPLAY", ":0");
        envVars.put("WINE_DISABLE_FULLSCREEN_HACK", "1");
        envVars.put("ENABLE_UTIL_LAYER", "1");

        String winePath = wineProfile == null ? imageFs.getWinePath() + "/bin"
                : ContentsManager.getSourceFile(context, wineProfile, wineProfile.wineBinPath).getAbsolutePath();
        envVars.put("PATH", winePath + ":" +
                rootDir.getPath() + "/usr/bin:" +
                rootDir.getPath() + "/usr/local/bin");

        envVars.put("BOX64_LD_LIBRARY_PATH", rootDir.getPath() + "/usr/lib/x86_64-linux-gnu");
        envVars.put("ANDROID_SYSVSHM_SERVER", rootDir.getPath() + UnixSocketConfig.SYSVSHM_SERVER_PATH);
        envVars.put("FONTCONFIG_PATH", imageFs.getEtcDir() + "/fonts");

        // Check for specific shared memory libraries
        if ((new File(imageFs.getLibDir(), "libandroid-sysvshm.so")).exists()) {
            envVars.put("LD_PRELOAD", imageFs.getLibDir() + "/libandroid-sysvshm.so");
        }

        envVars.put("XAUTHORITY","/data/user/0/com.winlator/files/imagefs/home/xuser/.Xauthority");

        // Merge any additional environment variables from external sources
        if (this.envVars != null) {
            envVars.putAll(this.envVars);
        }

        // Construct the command for Box64 with the guest executable
        String command = rootDir.getPath() + "/usr/local/bin/box64 " + guestExecutable;

        // Set execute permissions for box64 if necessary
        File box64File = new File(rootDir, "/usr/local/bin/box64");
        if (box64File.exists()) {
            FileUtils.chmod(box64File, 0755);
        }

        return ProcessHelper.exec(command, envVars.toStringArray(), rootDir, (status) -> {
            synchronized (lock) {
                pid = -1;
            }
            // Only call terminationCallback if Winetricks is NOT running
            if (!environment.isWinetricksRunning()) {
                if (terminationCallback != null) {
                    terminationCallback.call(status);
                }
            } else {
                Log.d("execGuestProgram", "Skipping termination callback because Winetricks is running.");
            }
        });
    }


    private void extractBox86_64Files() {
        ImageFs imageFs = environment.getImageFs();
        Context context = environment.getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);


        String box86Version = preferences.getString("box86_version", DefaultVersion.BOX86);
//        String box64Version = preferences.getString("box64_version", DefaultVersion.BOX64);

        // Fallback to default if the shared preference is not set or is empty
        box64Version = preferences.getString("box64_version", DefaultVersion.BOX64);
        if (box64Version == null || box64Version.isEmpty()) {
            box64Version = DefaultVersion.BOX64; // Assign the default version directly
            Log.w("GlibcProgramLauncherComponent", "box64Version was null or empty, using default: " + box64Version);
        }

        // If a shortcut is provided, it overrides the SharedPreferences value
        if (shortcut != null && shortcut.getExtra("box64Version") != null) {
            String shortcutVersion = shortcut.getExtra("box64Version");
            if (shortcutVersion != null && !shortcutVersion.isEmpty()) {
                box64Version = shortcutVersion;
            } else {
                Log.w("GlibcProgramLauncherComponent", "Shortcut box64Version was empty, keeping SharedPreferences/default value: " + box64Version);
            }
        }

        Log.d("GlibcProgramLauncherComponent", "box64Version in use: " + box64Version);

        String currentBox86Version = preferences.getString("current_box86_version", "");
        String currentBox64Version = preferences.getString("current_box64_version", "");
        File rootDir = imageFs.getRootDir();

        if (wow64Mode) {
            File box86File = new File(rootDir, "/usr/local/bin/box86");
            if (box86File.isFile()) {
                box86File.delete();
                preferences.edit().putString("current_box86_version", "").apply();
            }
        } else if (!box86Version.equals(currentBox86Version)) {
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "box86_64/box86-" + box86Version + ".tzst", rootDir);
            preferences.edit().putString("current_box86_version", box86Version).apply();
        }

        if (!box64Version.equals(currentBox64Version)) {
            ContentProfile profile = contentsManager.getProfileByEntryName("box64-" + box64Version);
            if (profile != null)
                contentsManager.applyContent(profile);
            else
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "box86_64/box64-" + box64Version + ".tzst", rootDir);
            preferences.edit().putString("current_box64_version", box64Version).apply();
        }

        // Set execute permissions for box64 just in case
        File box64File = new File(rootDir, "/usr/local/bin/box64");
        if (box64File.exists()) {
            FileUtils.chmod(box64File, 0755);
        }
        Log.d("GlibcProgramLauncherComponent", "box64File exists: " + box64File.exists());
    }

    private void addBox86EnvVars(EnvVars envVars, boolean enableLogs) {
        envVars.put("BOX86_NOBANNER", ProcessHelper.PRINT_DEBUG && enableLogs ? "0" : "1");
        envVars.put("BOX86_DYNAREC", "1");

        if (enableLogs) {
            envVars.put("BOX86_LOG", "1");
            envVars.put("BOX86_DYNAREC_MISSING", "1");
        }

        envVars.putAll(Box86_64PresetManager.getEnvVars("box86", environment.getContext(), box86Preset));
        envVars.put("BOX86_X11GLX", "1");
        envVars.put("BOX86_NORCFILES", "1");
    }

    private void addBox64EnvVars(EnvVars envVars, boolean enableLogs) {
        envVars.put("BOX64_NOBANNER", ProcessHelper.PRINT_DEBUG && enableLogs ? "0" : "1");
        envVars.put("BOX64_DYNAREC", "1");

        if (enableLogs) {
            envVars.put("BOX64_LOG", "1");
            envVars.put("BOX64_DYNAREC_MISSING", "1");
        }

        envVars.putAll(Box86_64PresetManager.getEnvVars("box64", environment.getContext(), box64Preset));
        envVars.put("BOX64_X11GLX", "1");
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

    public String execShellCommand(String command) {
        StringBuilder output = new StringBuilder();
        EnvVars envVars = new EnvVars();
        ImageFs imageFs = environment.getImageFs();

        envVars.put("PATH", imageFs.getRootDir().getPath() + "/usr/bin:/usr/local/bin:" + imageFs.getWinePath() + "/bin");

        // Execute the command and capture its output
        try {
            java.lang.Process process = Runtime.getRuntime().exec(command, envVars.toStringArray(), imageFs.getRootDir());
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
            output.append("Error: ").append(e.getMessage());
        }

        return output.toString();
    }

    public void runWinetricks(String arguments, Callback<Integer> callback) {
        Context context = environment.getContext();
        ImageFs imageFs = environment.getImageFs();
        File rootDir = imageFs.getRootDir();

        EnvVars envVars = new EnvVars();
        envVars.put("HOME", imageFs.home_path);
        envVars.put("USER", ImageFs.USER);
        envVars.put("TMPDIR", rootDir.getPath() + "/tmp");
        envVars.put("DISPLAY", ":0");
        envVars.put("PATH", rootDir.getPath() + "/usr/bin:" + rootDir.getPath() + "/usr/local/bin:" + imageFs.getWinePath() + "/bin");
        envVars.put("WINEPREFIX", imageFs.home_path + "/.wine");
        // Include other necessary environment variables

        // Ensure winetricks is executable
        File winetricksFile = new File(rootDir, "/usr/bin/winetricks");
        if (winetricksFile.exists()) {
            winetricksFile.setExecutable(true);
        } else {
            // Handle error: winetricks not found
        }

        // Build the command to execute winetricks
        String command = rootDir.getPath() + "/usr/bin/winetricks " + arguments;

        // Execute the command
        ProcessHelper.exec(command, envVars.toStringArray(), rootDir, (status) -> {
            // Callback after execution
            if (callback != null) {
                callback.call(status);
            }
        });
    }

    public void startWineServer() {
        if (pid != -1) {
            Log.d("GlibcProgramLauncherComponent", "wineserver is already running.");
            return; // Exit if wineserver is already running
        }

        // Define the command for wineserver
        String command = environment.getImageFs().getWinePath() + "/bin/wineserver";

        // Set up environment variables
        EnvVars envVars = new EnvVars();
        envVars.put("HOME", environment.getImageFs().home_path);
        envVars.put("DISPLAY", ":0");
        envVars.put("WINEPREFIX", environment.getImageFs().home_path + "/.wine");

        // Run wineserver as a persistent process
        pid = ProcessHelper.exec(command, envVars.toStringArray(), environment.getImageFs().getRootDir(), null);
        Log.d("GlibcProgramLauncherComponent", "Started wineserver with PID: " + pid);
    }

    public void stopWineServer() {
        if (pid == -1) {
            Log.d("GlibcProgramLauncherComponent", "wineserver is not running.");
            return; // Exit if wineserver is not running
        }

        // Kill wineserver process
        Process.killProcess(pid);
        pid = -1; // Reset pid to indicate wineserver has been stopped
        Log.d("GlibcProgramLauncherComponent", "Stopped wineserver.");
    }

    public void restartWineServer() {
        stopWineServer(); // Stop wineserver if running
        startWineServer(); // Start wineserver again
        Log.d("GlibcProgramLauncherComponent", "wineserver restarted.");
    }



    private void checkXServerConnectionUnixSocket() {
        File xSocket = new File("/data/data/com.winlator/files/imagefs/tmp/.X11-unix/X0");
        if (xSocket.exists()) {
            Log.d("XServerCheck", "X server socket exists.");
        } else {
            Log.e("XServerCheck", "X server socket not found.");
        }

    }


}