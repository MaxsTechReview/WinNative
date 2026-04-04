package com.winlator.cmod.container;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.MSLink;
import com.winlator.cmod.core.OnExtractFileListener;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.core.WineUtils;
import com.winlator.cmod.xenvironment.ImageFs;

import java.util.Arrays;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ContainerManager {
    public static final String WINEPREFIX_SEED_LAYOUT_VERSION = "ludashi-pattern-v1";
    private final ArrayList<Container> containers = new ArrayList<>();
    private int maxContainerId = 0;
    private final File homeDir;
    private final Context context;

    private boolean isInitialized = false; // New flag to track initialization

    public ContainerManager(Context context) {
        this.context = context;
        File rootDir = ImageFs.find(context).getRootDir();
        homeDir = new File(rootDir, "home");
        loadContainers();
        isInitialized = true;
    }

    // Check if the ContainerManager is fully initialized
    public boolean isInitialized() {
        return isInitialized;
    }

    public ArrayList<Container> getContainers() {
        return containers;
    }

    // Load containers from the home directory
    private void loadContainers() {
        containers.clear();
        maxContainerId = 0;

        File[] files = homeDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (file.getName().startsWith(ImageFs.USER + "-")) {
                        try {
                            Container container = new Container(
                                    Integer.parseInt(file.getName().replace(ImageFs.USER + "-", "")), this
                            );

                            container.setRootDir(new File(homeDir, ImageFs.USER + "-" + container.id));
                            String configStr = FileUtils.readString(container.getConfigFile());
                            if (configStr == null) {
                                Log.w("ContainerManager", "Skipping container " + container.id + ": missing or unreadable config file");
                                continue;
                            }
                            JSONObject data = new JSONObject(configStr);
                            container.loadData(data);
                            containers.add(container);
                            maxContainerId = Math.max(maxContainerId, container.id);
                        } catch (Exception e) {
                            Log.e("ContainerManager", "Error loading container " + file.getName(), e);
                        }
                    }
                }
            }
        }
    }


    public Context getContext() {
        return context;
    }


    public void activateContainer(Container container) {
        Log.d("ContainerManager", "activateContainer: id=" + container.id);
        File containerDir = new File(homeDir, ImageFs.USER+"-"+container.id);
        container.setRootDir(containerDir);
        File file = new File(homeDir, ImageFs.USER);

        enforceContainerDriveCPermissions(containerDir);

        // Replace the real "xuser" dir (from imagefs.txz) with a symlink to the active
        // container. Shared guest tools such as wfm.exe/winhandler.exe are restored from
        // container_pattern_common.tzst, not borrowed from another container.
        if (file.exists() && !FileUtils.isSymlink(file)) {
            Log.w("ContainerManager", "activateContainer: xuser is real dir, replacing it with a symlink for container " + container.id);
            boolean deleted = FileUtils.delete(file);
            Log.d("ContainerManager", "activateContainer: real xuser dir delete=" + deleted);
        } else {
            boolean deleted = file.delete();
            Log.d("ContainerManager", "activateContainer: xuser symlink/missing delete=" + deleted + " existed=" + file.exists());
        }
        FileUtils.symlink("./"+ImageFs.USER+"-"+container.id, file.getPath());
        Log.d("ContainerManager", "activateContainer: xuser symlink created, isSymlink=" + FileUtils.isSymlink(file) + " target=./" + ImageFs.USER + "-" + container.id);
    }

    public void createContainerAsync(final JSONObject data, ContentsManager contentsManager, Callback<Container> callback) {
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            final Container container = createContainer(data, contentsManager);
            handler.post(() -> callback.call(container));
        });
    }

    public void duplicateContainerAsync(Container container, Runnable callback) {
        duplicateContainerAsync(container, null, callback);
    }

    public void duplicateContainerAsync(Container container, Callback<Integer> progressCallback, Runnable callback) {
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            Callback<Integer> uiProgress = progressCallback != null
                    ? progress -> handler.post(() -> progressCallback.call(progress))
                    : null;
            duplicateContainer(container, uiProgress);
            handler.post(callback);
        });
    }

    public void removeContainerAsync(Container container, Runnable callback) {
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            removeContainer(container);
            handler.post(callback);
        });
    }

    public Container createContainer(JSONObject data, ContentsManager contentsManager) {
        try {
            int id = maxContainerId + 1;
            File containerDir = new File(homeDir, ImageFs.USER + "-" + id);
            
            Log.d("ContainerManager", "createContainer: homeDir=" + homeDir.getAbsolutePath() + " exists=" + homeDir.exists());
            
            // If a previous creation crashed, the directory might exist but not be registered.
            while (containerDir.exists()) {
                id++;
                containerDir = new File(homeDir, ImageFs.USER + "-" + id);
            }

            data.put("id", id);
            data.put("name", makeUniqueContainerName(data.optString("name", "Container-" + id)));
            if (!containerDir.mkdirs()) {
                Log.e("ContainerManager", "createContainer: FAILED to create dir: " + containerDir.getAbsolutePath());
                // Try creating parent dirs first
                if (!homeDir.exists()) {
                    Log.d("ContainerManager", "createContainer: homeDir does not exist, creating...");
                    homeDir.mkdirs();
                }
                if (!containerDir.mkdirs() && !containerDir.exists()) {
                    Log.e("ContainerManager", "createContainer: STILL failed to create dir after retry");
                    return null;
                }
            }
            Log.d("ContainerManager", "createContainer: dir created at " + containerDir.getAbsolutePath());

            Container container = new Container(id, this);
            container.setRootDir(containerDir);
            container.loadData(data);

            String wineVersion = data.getString("wineVersion");
            Log.d("ContainerManager", "createContainer: wineVersion=" + wineVersion);
            container.setWineVersion(wineVersion);

            if (!extractContainerPatternFile(container, container.getWineVersion(), contentsManager, containerDir, null)) {
                Log.e("ContainerManager", "createContainer: extractContainerPatternFile FAILED for wineVersion=" + container.getWineVersion());
                FileUtils.delete(containerDir);
                return null;
            }
            Log.d("ContainerManager", "createContainer: container pattern extracted successfully");
            container.putExtra("wineprefixArch", WineInfo.fromIdentifier(context, contentsManager, wineVersion).getArch());
            container.putExtra("wineprefixNeedsUpdate", null);
            container.putExtra("wineprefixSeedLayout", WINEPREFIX_SEED_LAYOUT_VERSION);
            enforceContainerDriveCPermissions(containerDir);

//            // Extract the selected graphics driver files
//            String driverVersion = container.getGraphicsDriverVersion();
//            if (!extractGraphicsDriverFiles(driverVersion, containerDir, null)) {
//                FileUtils.delete(containerDir);
//                return null;
//            }

            container.saveData();
            maxContainerId++;
            containers.add(container);
            return container;
        } catch (Throwable e) {
            Log.e("ContainerManager", "Error creating container", e);
        }
        return null;
    }


    private void duplicateContainer(Container srcContainer) {
        duplicateContainer(srcContainer, null);
    }

    private void duplicateContainer(Container srcContainer, Callback<Integer> progressCallback) {
        int id = maxContainerId + 1;

        File dstDir = new File(homeDir, ImageFs.USER + "-" + id);
        if (!dstDir.mkdirs()) return;

        final int totalFiles = FileUtils.countFiles(srcContainer.getRootDir());
        final int[] copiedFiles = {0};

        if (!FileUtils.copy(srcContainer.getRootDir(), dstDir, file -> {
            FileUtils.chmod(file, 0771);
            if (progressCallback != null && totalFiles > 0) {
                copiedFiles[0]++;
                int pct = Math.min(100, (copiedFiles[0] * 100) / totalFiles);
                progressCallback.call(pct);
            }
        })) {
            FileUtils.delete(dstDir);
            return;
        }

        Container dstContainer = new Container(id, this);
        dstContainer.setRootDir(dstDir);
        dstContainer.setName(makeUniqueContainerName(srcContainer.getName() + " (" + context.getString(R.string.common_ui_copy) + ")"));
        dstContainer.setScreenSize(srcContainer.getScreenSize());
        dstContainer.setEnvVars(srcContainer.getEnvVars());
        dstContainer.setCPUList(srcContainer.getCPUList());
        dstContainer.setCPUListWoW64(srcContainer.getCPUListWoW64());
        dstContainer.setGraphicsDriver(srcContainer.getGraphicsDriver());
        dstContainer.setDXWrapper(srcContainer.getDXWrapper());
        dstContainer.setDXWrapperConfig(srcContainer.getDXWrapperConfig());
        dstContainer.setAudioDriver(srcContainer.getAudioDriver());
        dstContainer.setWinComponents(srcContainer.getWinComponents());
        dstContainer.setDrives(srcContainer.getDrives());
        dstContainer.setShowFPS(srcContainer.isShowFPS());
        dstContainer.setStartupSelection(srcContainer.getStartupSelection());
        dstContainer.setBox64Preset(srcContainer.getBox64Preset());
        dstContainer.setDesktopTheme(srcContainer.getDesktopTheme());
        dstContainer.setWineVersion(srcContainer.getWineVersion());
        enforceContainerDriveCPermissions(dstDir);
        dstContainer.saveData();

        maxContainerId++;
        containers.add(dstContainer);
    }


    private void removeContainer(Container container) {
        if (FileUtils.delete(container.getRootDir())) containers.remove(container);
    }

    public ArrayList<Shortcut> loadShortcuts() {
        ArrayList<Shortcut> shortcuts = new ArrayList<>();
        for (Container container : containers) {
            File desktopDir = container.getDesktopDir();
            ArrayList<File> files = new ArrayList<>();
            if (desktopDir.exists())
                files.addAll(Arrays.asList(desktopDir.listFiles()));
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    if (fileName.endsWith(".lnk")) {
                        String filePath = file.getPath();
                        File desktopFile = new File(filePath.substring(0, filePath.lastIndexOf(".")) + ".desktop");
                        if (!desktopFile.exists()) {
                            MSLink.createDesktopFile(file, context);
                            shortcuts.add(new Shortcut(container, desktopFile));
                        }
                    }
                    else if (fileName.endsWith(".desktop")) shortcuts.add(new Shortcut(container, file));
                }
            }
        }

        shortcuts.sort(Comparator.comparing(a -> a.name));
        return shortcuts;
    }

    public int getNextContainerId() {
        return maxContainerId + 1;
    }

    public Container getContainerByWineVersion(String wineVersion) {
        if (wineVersion == null || wineVersion.isEmpty()) return null;
        for (Container container : containers) {
            if (wineVersion.equalsIgnoreCase(container.getWineVersion())) {
                return container;
            }
        }
        return null;
    }

    public String makeUniqueContainerName(String desiredName) {
        String baseName = sanitizeContainerName(desiredName);
        String uniqueName = baseName;
        int counter = 2;

        while (hasConflictingContainerName(uniqueName)) {
            uniqueName = baseName + " " + counter;
            counter++;
        }
        return uniqueName;
    }

    public Container getContainerById(int id) {
        for (Container container : containers) if (container.id == id) return container;
        return null;
    }

    public void normalizeContainerArchitectureSettings(Container container, WineInfo wineInfo, boolean persist) {
        if (container == null || wineInfo == null) return;

        boolean isArm64EC = wineInfo.isArm64EC();
        container.setEmulator(WineInfo.normalizeEmulatorSelection(isArm64EC, container.getEmulator(), false));
        container.setEmulator64(WineInfo.normalizeEmulatorSelection(isArm64EC, container.getEmulator64(), true));
        container.putExtra("wineprefixArch", wineInfo.getArch());
        container.putExtra("wineprefixSeedLayout", WINEPREFIX_SEED_LAYOUT_VERSION);

        if (isArm64EC) {
            String box64Version = container.getBox64Version();
            if (box64Version == null || box64Version.isEmpty() || DefaultVersion.BOX64.equalsIgnoreCase(box64Version)) {
                container.setBox64Version(DefaultVersion.WOWBOX64);
            }
        } else {
            String box64Version = container.getBox64Version();
            if (box64Version == null || box64Version.isEmpty() || DefaultVersion.WOWBOX64.equalsIgnoreCase(box64Version)) {
                container.setBox64Version(DefaultVersion.BOX64);
            }
        }

        if (persist) {
            container.saveData();
        }
    }

    private void extractCommonDlls(WineInfo wineInfo, String srcName, String dstName, File containerDir, OnExtractFileListener onExtractFileListener) throws JSONException {
        File srcDir = new File(wineInfo.path + "/lib/wine/" + srcName);
        File[] srcfiles = srcDir.listFiles(file -> file.isFile());

        if (srcfiles != null) {
            for (File file : srcfiles) {
            String dllName = file.getName();
            if (dllName.equals("iexplore.exe") && wineInfo.isArm64EC() && srcName.equals("aarch64-windows"))
                file = new File(wineInfo.path + "/lib/wine/" + "i386-windows/iexplore.exe");
            if (dllName.equals("tabtip.exe") || dllName.equals("icu.dll"))
                continue;
            File dstFile = new File(containerDir, ".wine/drive_c/windows/" + dstName + "/" + dllName);
            if (dstFile.exists()) continue;
            if (onExtractFileListener != null ) {
                dstFile = onExtractFileListener.onExtractFile(dstFile, 0);
                if (dstFile == null) continue;
            }
            FileUtils.copy(file, dstFile);
            }
        }
    }

    public boolean extractContainerPatternFile(Container container, String wineVersion, ContentsManager contentsManager, File containerDir, OnExtractFileListener onExtractFileListener) {
        WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion);
        String containerPattern = wineVersion + "_container_pattern.tzst";
        boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, containerPattern, containerDir, onExtractFileListener);
        if (!result) {
            File containerPatternFile = new File(wineInfo.path + "/prefixPack.txz");
            result = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, containerPatternFile, containerDir);
        }

        if (result) {
            try {
                if (wineInfo.isArm64EC())
                    extractCommonDlls(wineInfo, "aarch64-windows", "system32", containerDir, onExtractFileListener); // arm64ec only
                else
                    extractCommonDlls(wineInfo, "x86_64-windows", "system32", containerDir, onExtractFileListener);

                extractCommonDlls(wineInfo, "i386-windows", "syswow64", containerDir, onExtractFileListener);
            }
            catch (JSONException e) {
                Log.e("ContainerManager", "extractContainerPatternFile: extractCommonDlls failed", e);
                // Don't fail the whole extraction just because of common DLLs — container is still usable
                Log.w("ContainerManager", "extractContainerPatternFile: continuing despite extractCommonDlls failure");
            }
        }
   
        return result;
    }

    public boolean repairContainerWinePrefix(Container container, String wineVersion, ContentsManager contentsManager, OnExtractFileListener onExtractFileListener) {
        File containerDir = container.getRootDir();
        if (containerDir == null || !containerDir.isDirectory()) return false;

        File tempDir = FileUtils.createTempFile(context.getCacheDir(), "wineprefix-repair");
        if (!tempDir.mkdirs()) {
            Log.e("ContainerManager", "repairContainerWinePrefix: failed to create temp dir " + tempDir.getAbsolutePath());
            return false;
        }

        boolean extracted = false;
        try {
            extracted = extractContainerPatternFile(container, wineVersion, contentsManager, tempDir, onExtractFileListener);
            if (!extracted) {
                Log.e("ContainerManager", "repairContainerWinePrefix: failed to extract repair prefix for " + wineVersion);
                return false;
            }

            File repairedPrefixDir = new File(tempDir, ".wine");
            if (!WineUtils.isPrefixValid(tempDir) || !repairedPrefixDir.isDirectory()) {
                Log.e("ContainerManager", "repairContainerWinePrefix: extracted prefix is still invalid");
                return false;
            }

            File targetPrefixDir = new File(containerDir, ".wine");
            if (targetPrefixDir.exists() && !FileUtils.delete(targetPrefixDir)) {
                Log.e("ContainerManager", "repairContainerWinePrefix: failed to clear existing prefix " + targetPrefixDir.getAbsolutePath());
                return false;
            }

            if (!FileUtils.copy(repairedPrefixDir, targetPrefixDir)) {
                Log.e("ContainerManager", "repairContainerWinePrefix: failed to copy repaired prefix");
                return false;
            }

            WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion);
            container.putExtra("wineprefixArch", wineInfo.getArch());
            container.putExtra("wineprefixNeedsUpdate", null);
            container.putExtra("wineprefixSeedLayout", WINEPREFIX_SEED_LAYOUT_VERSION);
            container.putExtra("appVersion", null);
            container.putExtra("imgVersion", null);
            container.putExtra("dxwrapper", null);
            container.putExtra("wincomponents", null);
            container.putExtra("desktopTheme", null);
            container.putExtra("startupSelection", null);
            container.putExtra("mono_installed", null);
            container.putExtra("mono_version", null);
            enforceContainerDriveCPermissions(containerDir);
            container.saveData();
            return true;
        } finally {
            FileUtils.delete(tempDir);
        }
    }

    public Container getContainerForShortcut(Shortcut shortcut) {
        // Search for the container by its ID
        for (Container container : containers) {
            if (container.id == shortcut.getContainerId()) {
                return container;
            }
        }
        return null;  // Return null if no matching container is found
    }

    // Utility method to run on UI thread
    private void runOnUiThread(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }

    private void enforceContainerDriveCPermissions(File containerDir) {
        File driveCDir = new File(containerDir, ".wine/drive_c");
        FileUtils.enforcePermissionsRecursive(driveCDir, 0771);
    }

    private boolean hasConflictingContainerName(String candidateName) {
        String candidateKey = normalizeContainerName(candidateName);
        for (Container container : containers) {
            if (candidateKey.equals(normalizeContainerName(container.getName()))) {
                return true;
            }
        }
        return false;
    }

    private static String sanitizeContainerName(String name) {
        String sanitized = name != null ? name.trim() : "";
        sanitized = sanitized.replaceAll("\\s+", " ");
        return sanitized.isEmpty() ? "Container" : sanitized;
    }

    public static String normalizeContainerName(String name) {
        String normalized = sanitizeContainerName(name)
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        return normalized;
    }



}
