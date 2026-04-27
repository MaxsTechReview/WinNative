package com.winlator.cmod.runtime.container;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.content.ContentProfile;
import com.winlator.cmod.runtime.content.ContentsManager;
import com.winlator.cmod.runtime.display.environment.ImageFs;
import com.winlator.cmod.runtime.wine.MSLink;
import com.winlator.cmod.runtime.wine.WineInfo;
import com.winlator.cmod.runtime.wine.WineUtils;
import com.winlator.cmod.shared.io.FileUtils;
import com.winlator.cmod.shared.io.TarCompressorUtils;
import com.winlator.cmod.shared.util.Callback;
import com.winlator.cmod.shared.util.OnExtractFileListener;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONException;
import org.json.JSONObject;

public class ContainerManager {
  private static final AtomicBoolean shortcutUpgradeRunning = new AtomicBoolean(false);
  private static final AtomicBoolean shortcutUpgradeAttempted = new AtomicBoolean(false);

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
              Container container =
                  new Container(
                      Integer.parseInt(file.getName().replace(ImageFs.USER + "-", "")), this);

              container.setRootDir(new File(homeDir, ImageFs.USER + "-" + container.id));
              String configStr = FileUtils.readString(container.getConfigFile());
              if (configStr == null) {
                Log.w(
                    "ContainerManager",
                    "Skipping container " + container.id + ": missing or unreadable config file");
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
    File containerDir = new File(homeDir, ImageFs.USER + "-" + container.id);
    container.setRootDir(containerDir);
    File file = new File(homeDir, ImageFs.USER);

    // Make C: Drive accessible — 0771 not 0777 to prevent other apps reading file contents
    try {
      Runtime.getRuntime()
          .exec(
              new String[] {
                "chmod", "-R", "0771", new File(containerDir, ".wine/drive_c").getAbsolutePath()
              });
    } catch (Exception e) {
    }

    // Replace the real "xuser" dir (from imagefs.txz) with a symlink to the active
    // container. Migrate winhandler.exe/wfm.exe first since they aren't in container
    // pattern archives. Only runs once — after that xuser is already a symlink.
    if (file.exists() && !FileUtils.isSymlink(file)) {
      Log.w(
          "ContainerManager",
          "activateContainer: xuser is real dir, migrating essential files to container "
              + container.id);
      migrateEssentialFiles(file, containerDir);
      boolean deleted = FileUtils.delete(file);
      Log.d("ContainerManager", "activateContainer: real xuser dir delete=" + deleted);
    } else {
      boolean deleted = file.delete();
      Log.d(
          "ContainerManager",
          "activateContainer: xuser symlink/missing delete="
              + deleted
              + " existed="
              + file.exists());
    }
    FileUtils.symlink("./" + ImageFs.USER + "-" + container.id, file.getPath());
    Log.d(
        "ContainerManager",
        "activateContainer: xuser symlink created, isSymlink="
            + FileUtils.isSymlink(file)
            + " target=./"
            + ImageFs.USER
            + "-"
            + container.id);
  }

  private void migrateEssentialFiles(File sourceDir, File destDir) {
    String[] essentialPaths = {
      ".wine/drive_c/windows/winhandler.exe", ".wine/drive_c/windows/wfm.exe"
    };
    for (String path : essentialPaths) {
      File source = new File(sourceDir, path);
      File dest = new File(destDir, path);
      if (source.exists() && !dest.exists()) {
        dest.getParentFile().mkdirs();
        FileUtils.copy(source, dest);
        Log.d("ContainerManager", "Migrated " + path + " to container");
      }
    }
  }

  public void createContainerAsync(
      final JSONObject data, ContentsManager contentsManager, Callback<Container> callback) {
    final Handler handler = new Handler(Looper.getMainLooper());
    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              final Container container = createContainer(data, contentsManager);
              handler.post(() -> callback.call(container));
            });
  }

  public void duplicateContainerAsync(Container container, Runnable callback) {
    duplicateContainerAsync(container, null, callback);
  }

  public void duplicateContainerAsync(
      Container container, Callback<Integer> progressCallback, Runnable callback) {
    final Handler handler = new Handler(Looper.getMainLooper());
    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              Callback<Integer> uiProgress =
                  progressCallback != null
                      ? progress -> handler.post(() -> progressCallback.call(progress))
                      : null;
              duplicateContainer(container, uiProgress);
              handler.post(callback);
            });
  }

  public void removeContainerAsync(Container container, Runnable callback) {
    final Handler handler = new Handler(Looper.getMainLooper());
    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              removeContainer(container);
              handler.post(callback);
            });
  }

  public Container createContainer(JSONObject data, ContentsManager contentsManager) {
    try {
      int id = maxContainerId + 1;
      File containerDir = new File(homeDir, ImageFs.USER + "-" + id);

      Log.d(
          "ContainerManager",
          "createContainer: homeDir=" + homeDir.getAbsolutePath() + " exists=" + homeDir.exists());

      // If a previous creation crashed, the directory might exist but not be registered.
      while (containerDir.exists()) {
        id++;
        containerDir = new File(homeDir, ImageFs.USER + "-" + id);
      }

      data.put("id", id);
      if (!containerDir.mkdirs()) {
        Log.e(
            "ContainerManager",
            "createContainer: FAILED to create dir: " + containerDir.getAbsolutePath());
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
      Log.d(
          "ContainerManager", "createContainer: dir created at " + containerDir.getAbsolutePath());

      Container container = new Container(id, this);
      container.setRootDir(containerDir);
      container.loadData(data);

      String wineVersion = data.getString("wineVersion");
      Log.d("ContainerManager", "createContainer: wineVersion=" + wineVersion);
      container.setWineVersion(wineVersion);

      // Set the correct emulators based on the wine architecture, unless the
      // caller already specified them in the JSON data.  This ensures every
      // creation path (manual, contents download, store integration) gets the
      // right emulator: box64 for x86_64, fexcore for arm64ec.
      if (!data.has("emulator") || !data.has("emulator64")) {
          WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion);
          if (wineInfo.isArm64EC()) {
              if (!data.has("emulator"))   container.setEmulator("fexcore");
              if (!data.has("emulator64")) container.setEmulator64("fexcore");
          } else {
              if (!data.has("emulator"))   container.setEmulator("box64");
              if (!data.has("emulator64")) container.setEmulator64("box64");
          }
          Log.d("ContainerManager", "createContainer: auto-set emulators for arch="
              + wineInfo.getArch() + " emulator=" + container.getEmulator()
              + " emulator64=" + container.getEmulator64());
      }

      if (!extractContainerPatternFile(
          container, container.getWineVersion(), contentsManager, containerDir, null)) {
        Log.e(
            "ContainerManager",
            "createContainer: extractContainerPatternFile FAILED for wineVersion="
                + container.getWineVersion());
        FileUtils.delete(containerDir);
        return null;
      }
      Log.d("ContainerManager", "createContainer: container pattern extracted successfully");
      container.putExtra(
          "wineprefixArch",
          WineInfo.fromIdentifier(context, contentsManager, wineVersion).getArch());
      container.putExtra("wineprefixNeedsUpdate", null);

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

    if (!FileUtils.copy(
        srcContainer.getRootDir(),
        dstDir,
        file -> {
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
    dstContainer.setName(
        srcContainer.getName() + " (" + context.getString(R.string.common_ui_copy) + ")");
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
      if (desktopDir.exists()) files.addAll(Arrays.asList(desktopDir.listFiles()));
      if (files != null) {
        for (File file : files) {
          String fileName = file.getName();
          if (fileName.endsWith(".lnk")) {
            String filePath = file.getPath();
            File desktopFile =
                new File(filePath.substring(0, filePath.lastIndexOf(".")) + ".desktop");
            if (!desktopFile.exists()) {
              MSLink.createDesktopFile(file, context, container);
              shortcuts.add(new Shortcut(container, desktopFile));
            }
          } else if (fileName.endsWith(".desktop")) {
            shortcuts.add(new Shortcut(container, file));
          }
        }
      }
    }

    shortcuts.sort(Comparator.comparing(a -> a.name));
    return shortcuts;
  }

  public void upgradeShortcuts(final Runnable onDone) {
    if (!shortcutUpgradeRunning.compareAndSet(false, true)) return;

    new Thread(() -> {
        try {
            boolean changed = false;
            for (Container container : getContainers()) {
                File desktopDir = container.getDesktopDir();
                if (!desktopDir.exists()) continue;
                File[] files = desktopDir.listFiles();
                if (files == null) continue;
                for (File file : files) {
                    if (file.getName().endsWith(".lnk")) {
                        File desktopFile = new File(file.getPath().substring(0, file.getPath().lastIndexOf(".")) + ".desktop");
                        boolean needsUpgrade = true;
                        if (desktopFile.exists()) {
                            String content = FileUtils.readString(desktopFile);
                            if (content != null && content.contains("game_source=CUSTOM")) {
                                needsUpgrade = false;
                            }
                        }
                        if (needsUpgrade) {
                            if (MSLink.createDesktopFile(file, context, container)) {
                                changed = true;
                            }
                        }
                    }
                }
            }
            if (changed && onDone != null) {
                new Handler(Looper.getMainLooper()).post(onDone);
            }
        } finally {
            shortcutUpgradeRunning.set(false);
        }
    }, "ShortcutUpgrade").start();
  }

  public int getNextContainerId() {
    return maxContainerId + 1;
  }

  public Container getContainerById(int id) {
    for (Container container : containers) if (container.id == id) return container;
    return null;
  }

  private void extractCommonDlls(
      WineInfo wineInfo,
      String srcName,
      String dstName,
      File containerDir,
      OnExtractFileListener onExtractFileListener)
      throws JSONException {
    File srcDir = new File(wineInfo.path + "/lib/wine/" + srcName);

    File[] srcfiles = srcDir.listFiles(file -> file.isFile());

    if (srcfiles != null) {
      for (File file : srcfiles) {
        String dllName = file.getName();
        if (dllName.equals("iexplore.exe")
            && wineInfo.isArm64EC()
            && srcName.equals("aarch64-windows"))
          file = new File(wineInfo.path + "/lib/wine/" + "i386-windows/iexplore.exe");
        if (dllName.equals("tabtip.exe") || dllName.equals("icu.dll")) continue;
        File dstFile = new File(containerDir, ".wine/drive_c/windows/" + dstName + "/" + dllName);
        if (dstFile.exists()) continue;
        if (onExtractFileListener != null) {
          dstFile = onExtractFileListener.onExtractFile(dstFile, 0);
          if (dstFile == null) continue;
        }
        FileUtils.copy(file, dstFile);
      }
    }

    if (wineInfo.isArm64EC() && "aarch64-windows".equals(srcName)) {
      File dstDir = new File(containerDir, ".wine/drive_c/windows/" + dstName);
      assertArm64PEMachine(dstDir, "xinput1_4.dll");
      assertArm64PEMachine(dstDir, "dinput8.dll");
    }
  }

  /**
   * Reads the PE <code>Machine</code> field and logs a loud warning if it is not ARM64
   * ({@code 0xAA64}) or ARM64EC ({@code 0xA641}). Missing files are treated as non-fatal.
   *
   * <p>Guardrail against mis-packaged tzsts like the Mar-2026 <code>xinput_virtual_arm64ec.tzst</code>,
   * which carried AMD64 PE binaries under an arm64ec filename. Silent mismatch manifested as
   * joy.cpl failing to load xinput and "Game Controllers" disappearing from the Start Menu.
   */
  private static void assertArm64PEMachine(File dir, String dllName) {
    File f = new File(dir, dllName);
    if (!f.isFile()) return;
    try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
      if (raf.length() < 0x40) return;
      raf.seek(0x3c);
      int peOffset = Integer.reverseBytes(raf.readInt());
      if (peOffset < 0 || peOffset + 6 > raf.length()) return;
      raf.seek(peOffset);
      if (raf.readByte() != 'P' || raf.readByte() != 'E'
          || raf.readByte() != 0 || raf.readByte() != 0) return;
      int machine = Short.toUnsignedInt(Short.reverseBytes(raf.readShort()));
      if (machine != 0xAA64 && machine != 0xA641) {
        Log.e(
            "ContainerManager",
            String.format(
                "PE-machine mismatch: %s in %s is 0x%04X (expected 0xAA64 ARM64 or 0xA641 ARM64EC)."
                    + " Controller support (joy.cpl/xinput) will not load. Source tzst is mis-packaged.",
                dllName, dir.getAbsolutePath(), machine));
      }
    } catch (Exception e) {
      Log.w("ContainerManager", "assertArm64PEMachine: " + f.getAbsolutePath() + ": " + e);
    }
  }

  public boolean extractContainerPatternFile(
      Container container,
      String wineVersion,
      ContentsManager contentsManager,
      File containerDir,
      OnExtractFileListener onExtractFileListener) {
    Log.d(
        "ContainerManager",
        "extractContainerPatternFile: wineVersion="
            + wineVersion
            + " containerDir="
            + containerDir.getAbsolutePath());
    WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion);
    Log.d(
        "ContainerManager",
        "extractContainerPatternFile: wineInfo="
            + wineInfo
            + " path="
            + (wineInfo != null ? wineInfo.path : "null"));

    // Step 1: Try to extract the versioned container pattern from bundled assets
    // e.g. "proton-9.0-x86_64_container_pattern.tzst"
    String containerPattern = wineVersion + "_container_pattern.tzst";
    Log.d("ContainerManager", "extractContainerPatternFile: trying asset: " + containerPattern);
    boolean result =
        TarCompressorUtils.extract(
            TarCompressorUtils.Type.ZSTD,
            context,
            containerPattern,
            containerDir,
            onExtractFileListener);
    Log.d("ContainerManager", "extractContainerPatternFile: asset extraction result=" + result);

    // Step 2: If asset extraction failed, look for the prefix pack from the installed custom proton
    if (!result) {
      ContentProfile profile = contentsManager.getProfileByEntryName(wineVersion);
      Log.d(
          "ContainerManager",
          "extractContainerPatternFile: profile lookup for '"
              + wineVersion
              + "' => "
              + (profile != null ? profile.verName : "null"));

      if (profile != null) {
        // Use the ContentsManager's install dir directly — this is always correct
        // for custom installed protons, unlike wineInfo.path which may fall back to default
        File profileInstallDir = ContentsManager.getInstallDir(context, profile);
        Log.d(
            "ContainerManager",
            "extractContainerPatternFile: profileInstallDir="
                + profileInstallDir.getAbsolutePath()
                + " exists="
                + profileInstallDir.exists());

        File containerPatternFile;
        if (profile.winePrefixPack != null && !profile.winePrefixPack.isEmpty()) {
          containerPatternFile = new File(profileInstallDir, profile.winePrefixPack);
        } else {
          containerPatternFile = new File(profileInstallDir, "prefixPack.txz");
        }
        Log.d(
            "ContainerManager",
            "extractContainerPatternFile: trying profile prefix pack: "
                + containerPatternFile.getAbsolutePath()
                + " exists="
                + containerPatternFile.exists());

        if (containerPatternFile.exists()) {
          if (containerPatternFile.getName().endsWith(".tzst")) {
            result =
                TarCompressorUtils.extract(
                    TarCompressorUtils.Type.ZSTD, containerPatternFile, containerDir);
          } else {
            result =
                TarCompressorUtils.extract(
                    TarCompressorUtils.Type.XZ, containerPatternFile, containerDir);
          }
          Log.d(
              "ContainerManager",
              "extractContainerPatternFile: profile prefix pack extraction result=" + result);
        }
      }

      // Also try from wineInfo.path as a secondary fallback (for bundled non-asset protons)
      if (!result && wineInfo != null && wineInfo.path != null && !wineInfo.path.isEmpty()) {
        File wineInfoPrefixPack;
        if (profile != null
            && profile.winePrefixPack != null
            && !profile.winePrefixPack.isEmpty()) {
          wineInfoPrefixPack = new File(wineInfo.path, profile.winePrefixPack);
        } else {
          wineInfoPrefixPack = new File(wineInfo.path, "prefixPack.txz");
        }
        Log.d(
            "ContainerManager",
            "extractContainerPatternFile: trying wineInfo.path fallback: "
                + wineInfoPrefixPack.getAbsolutePath()
                + " exists="
                + wineInfoPrefixPack.exists());
        if (wineInfoPrefixPack.exists()) {
          if (wineInfoPrefixPack.getName().endsWith(".tzst")) {
            result =
                TarCompressorUtils.extract(
                    TarCompressorUtils.Type.ZSTD, wineInfoPrefixPack, containerDir);
          } else {
            result =
                TarCompressorUtils.extract(
                    TarCompressorUtils.Type.XZ, wineInfoPrefixPack, containerDir);
          }
          Log.d(
              "ContainerManager",
              "extractContainerPatternFile: wineInfo.path fallback extraction result=" + result);
        }
      }
    }

    // Step 3: If we still don't have a container pattern, use the common one as last resort
    if (!result) {
      Log.d(
          "ContainerManager",
          "extractContainerPatternFile: all pattern sources failed, trying container_pattern_common.tzst as last resort");
      result =
          TarCompressorUtils.extract(
              TarCompressorUtils.Type.ZSTD,
              context,
              "container_pattern_common.tzst",
              containerDir,
              onExtractFileListener);
      Log.d(
          "ContainerManager",
          "extractContainerPatternFile: common pattern extraction result=" + result);
    }

    if (result) {
      try {
        if (wineInfo.isArm64EC())
          extractCommonDlls(
              wineInfo,
              "aarch64-windows",
              "system32",
              containerDir,
              onExtractFileListener); // arm64ec only
        else
          extractCommonDlls(
              wineInfo, "x86_64-windows", "system32", containerDir, onExtractFileListener);

        extractCommonDlls(
            wineInfo, "i386-windows", "syswow64", containerDir, onExtractFileListener);
      } catch (JSONException e) {
        Log.e("ContainerManager", "extractContainerPatternFile: extractCommonDlls failed", e);
        // Don't fail the whole extraction just because of common DLLs — container is still usable
        Log.w(
            "ContainerManager",
            "extractContainerPatternFile: continuing despite extractCommonDlls failure");
      }
    }

    return result;
  }

  public boolean repairContainerWinePrefix(
      Container container,
      String wineVersion,
      ContentsManager contentsManager,
      OnExtractFileListener onExtractFileListener) {
    File containerDir = container.getRootDir();
    if (containerDir == null || !containerDir.isDirectory()) return false;

    File tempDir = FileUtils.createTempFile(context.getCacheDir(), "wineprefix-repair");
    if (!tempDir.mkdirs()) {
      Log.e(
          "ContainerManager",
          "repairContainerWinePrefix: failed to create temp dir " + tempDir.getAbsolutePath());
      return false;
    }

    boolean extracted = false;
    try {
      extracted =
          extractContainerPatternFile(
              container, wineVersion, contentsManager, tempDir, onExtractFileListener);
      if (!extracted) {
        Log.e(
            "ContainerManager",
            "repairContainerWinePrefix: failed to extract repair prefix for " + wineVersion);
        return false;
      }

      File repairedPrefixDir = new File(tempDir, ".wine");
      if (!WineUtils.isPrefixValid(tempDir) || !repairedPrefixDir.isDirectory()) {
        Log.e("ContainerManager", "repairContainerWinePrefix: extracted prefix is still invalid");
        return false;
      }

      File targetPrefixDir = new File(containerDir, ".wine");
      if (targetPrefixDir.exists() && !FileUtils.delete(targetPrefixDir)) {
        Log.e(
            "ContainerManager",
            "repairContainerWinePrefix: failed to clear existing prefix "
                + targetPrefixDir.getAbsolutePath());
        return false;
      }

      if (!FileUtils.copy(repairedPrefixDir, targetPrefixDir)) {
        Log.e("ContainerManager", "repairContainerWinePrefix: failed to copy repaired prefix");
        return false;
      }

      WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion);
      container.putExtra("wineprefixArch", wineInfo.getArch());
      container.putExtra("wineprefixNeedsUpdate", null);
      container.putExtra("appVersion", null);
      container.putExtra("imgVersion", null);
      container.putExtra("dxwrapper", null);
      container.putExtra("wincomponents", null);
      container.putExtra("desktopTheme", null);
      container.putExtra("startupSelection", null);
      container.putExtra("mono_installed", null);
      container.putExtra("mono_version", null);
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
    return null; // Return null if no matching container is found
  }

  // Utility method to run on UI thread
  private void runOnUiThread(Runnable action) {
    new Handler(Looper.getMainLooper()).post(action);
  }
}
