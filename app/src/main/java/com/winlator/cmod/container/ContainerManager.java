package com.winlator.cmod.container;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.MSLink;
import com.winlator.cmod.core.OnExtractFileListener;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.xenvironment.ImageFs;

import java.util.Arrays;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.Executors;

public class ContainerManager {
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
        container.setRootDir(new File(homeDir, ImageFs.USER+"-"+container.id));
        File file = new File(homeDir, ImageFs.USER);
        file.delete();
        FileUtils.symlink("./"+ImageFs.USER+"-"+container.id, file.getPath());
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
        dstContainer.setName(srcContainer.getName() + " (" + context.getString(R.string._copy) + ")");
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

    public Container getContainerById(int id) {
        for (Container container : containers) if (container.id == id) return container;
        return null;
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
        Log.d("ContainerManager", "extractContainerPatternFile: wineVersion=" + wineVersion + " containerDir=" + containerDir.getAbsolutePath());
        WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion);
        Log.d("ContainerManager", "extractContainerPatternFile: wineInfo=" + wineInfo + " path=" + (wineInfo != null ? wineInfo.path : "null"));

        // Step 1: Try to extract the versioned container pattern from bundled assets
        // e.g. "proton-9.0-x86_64_container_pattern.tzst"
        String containerPattern = wineVersion + "_container_pattern.tzst";
        Log.d("ContainerManager", "extractContainerPatternFile: trying asset: " + containerPattern);
        boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, containerPattern, containerDir, onExtractFileListener);
        Log.d("ContainerManager", "extractContainerPatternFile: asset extraction result=" + result);

        // Step 2: If asset extraction failed, look for the prefix pack from the installed custom proton
        if (!result) {
            ContentProfile profile = contentsManager.getProfileByEntryName(wineVersion);
            Log.d("ContainerManager", "extractContainerPatternFile: profile lookup for '" + wineVersion + "' => " + (profile != null ? profile.verName : "null"));

            if (profile != null) {
                // Use the ContentsManager's install dir directly — this is always correct
                // for custom installed protons, unlike wineInfo.path which may fall back to default
                File profileInstallDir = ContentsManager.getInstallDir(context, profile);
                Log.d("ContainerManager", "extractContainerPatternFile: profileInstallDir=" + profileInstallDir.getAbsolutePath() + " exists=" + profileInstallDir.exists());

                File containerPatternFile;
                if (profile.winePrefixPack != null && !profile.winePrefixPack.isEmpty()) {
                    containerPatternFile = new File(profileInstallDir, profile.winePrefixPack);
                } else {
                    containerPatternFile = new File(profileInstallDir, "prefixPack.txz");
                }
                Log.d("ContainerManager", "extractContainerPatternFile: trying profile prefix pack: " + containerPatternFile.getAbsolutePath() + " exists=" + containerPatternFile.exists());

                if (containerPatternFile.exists()) {
                    if (containerPatternFile.getName().endsWith(".tzst")) {
                        result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, containerPatternFile, containerDir);
                    } else {
                        result = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, containerPatternFile, containerDir);
                    }
                    Log.d("ContainerManager", "extractContainerPatternFile: profile prefix pack extraction result=" + result);
                }
            }

            // Also try from wineInfo.path as a secondary fallback (for bundled non-asset protons)
            if (!result && wineInfo != null && wineInfo.path != null && !wineInfo.path.isEmpty()) {
                File wineInfoPrefixPack;
                if (profile != null && profile.winePrefixPack != null && !profile.winePrefixPack.isEmpty()) {
                    wineInfoPrefixPack = new File(wineInfo.path, profile.winePrefixPack);
                } else {
                    wineInfoPrefixPack = new File(wineInfo.path, "prefixPack.txz");
                }
                Log.d("ContainerManager", "extractContainerPatternFile: trying wineInfo.path fallback: " + wineInfoPrefixPack.getAbsolutePath() + " exists=" + wineInfoPrefixPack.exists());
                if (wineInfoPrefixPack.exists()) {
                    if (wineInfoPrefixPack.getName().endsWith(".tzst")) {
                        result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, wineInfoPrefixPack, containerDir);
                    } else {
                        result = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, wineInfoPrefixPack, containerDir);
                    }
                    Log.d("ContainerManager", "extractContainerPatternFile: wineInfo.path fallback extraction result=" + result);
                }
            }
        }

        // Step 3: If we still don't have a container pattern, use the common one as last resort
        if (!result) {
            Log.d("ContainerManager", "extractContainerPatternFile: all pattern sources failed, trying container_pattern_common.tzst as last resort");
            result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "container_pattern_common.tzst", containerDir, onExtractFileListener);
            Log.d("ContainerManager", "extractContainerPatternFile: common pattern extraction result=" + result);
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



}
