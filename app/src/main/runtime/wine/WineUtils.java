package com.winlator.cmod.runtime.wine;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import com.winlator.cmod.runtime.container.Container;
import com.winlator.cmod.runtime.display.environment.ImageFs;
import com.winlator.cmod.runtime.system.GPUInformation;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class WineUtils {

  public static String hostPathToRootWinePath(
      @Nullable Container container, @Nullable String hostPath) {
    return hostPathToMappedWinePath(container, hostPath);
  }

  public static String hostPathToMappedWinePath(
      @Nullable Container container, @Nullable String hostPath) {
    if (hostPath == null || hostPath.isEmpty()) return "";

    String normalizedHostPath = normalizeHostPath(hostPath);
    String bestDriveLetter = null;
    String bestDriveRoot = null;

    String drives =
        container != null && container.getDrives() != null
            ? container.getDrives()
            : Container.DEFAULT_DRIVES;

    for (String[] drive : Container.drivesIterator(drives)) {
      if (drive.length < 2) continue;
      String driveLetter = drive[0];
      if ("A".equalsIgnoreCase(driveLetter)) continue;
      String driveRoot = normalizeHostPath(drive[1]);
      if (!pathStartsWith(normalizedHostPath, driveRoot)) continue;

      if (bestDriveRoot == null || driveRoot.length() > bestDriveRoot.length()) {
        bestDriveLetter = driveLetter;
        bestDriveRoot = driveRoot;
      }
    }

    if (bestDriveLetter != null && bestDriveRoot != null) {
      String relativePath = normalizedHostPath.substring(bestDriveRoot.length()).replace("/", "\\");
      while (relativePath.startsWith("\\")) relativePath = relativePath.substring(1);
      if (relativePath.isEmpty()) return bestDriveLetter + ":\\";
      return bestDriveLetter + ":\\" + relativePath;
    }

    String windowsPath = normalizedHostPath.replace("/", "\\");
    if (!windowsPath.startsWith("\\")) windowsPath = "\\" + windowsPath;
    return "Z:" + windowsPath;
  }

  private static boolean pathStartsWith(String path, String basePath) {
    if (path.equals(basePath)) return true;
    if (basePath.endsWith(File.separator)) return path.startsWith(basePath);
    return path.startsWith(basePath + File.separator);
  }

  private static String normalizeHostPath(String path) {
    if (path == null || path.isEmpty()) return "";
    try {
      return new File(path).getCanonicalPath();
    } catch (IOException e) {
      return new File(path).getAbsolutePath();
    }
  }

  public static String normalizePersistentDrives(Context context, String drives) {
    List<String[]> entries = new ArrayList<>();
    if (drives != null && !drives.isEmpty()) {
      for (String[] drive : Container.drivesIterator(drives)) {
        if (drive[1] == null || drive[1].isEmpty()) continue;
        if ("A".equals(drive[0]) || "E".equals(drive[0])) continue;
        entries.add(new String[] {drive[0], drive[1]});
      }
    }

    String downloadsPath =
        android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS)
            .getAbsolutePath();
    String externalStoragePath =
        android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
    String sdCardRootPath = getSdCardRootPath();

    upsertDrive(entries, "D", downloadsPath);
    upsertDrive(entries, "F", externalStoragePath);
    if (sdCardRootPath != null && !sdCardRootPath.isEmpty())
      upsertDrive(entries, "G", sdCardRootPath);
    else removeDrive(entries, "G");

    StringBuilder normalized = new StringBuilder();
    appendDriveIfPresent(normalized, entries, "C");
    appendDriveIfPresent(normalized, entries, "D");
    appendDriveIfPresent(normalized, entries, "F");
    appendDriveIfPresent(normalized, entries, "G");

    for (String[] entry : entries) {
      if ("C".equals(entry[0])
          || "D".equals(entry[0])
          || "F".equals(entry[0])
          || "G".equals(entry[0])) {
        continue;
      }
      normalized.append(entry[0]).append(':').append(entry[1]);
    }

    return normalized.toString();
  }

  public static String getPrimaryGameDrivePath(Container container) {
    if (container == null) return null;

    String fallback = null;
    String drives =
        container.getDrives() != null ? container.getDrives() : Container.DEFAULT_DRIVES;
    for (String[] drive : Container.drivesIterator(drives)) {
      String letter = drive[0];
      if ("G".equals(letter)) return drive[1];
      if ("F".equals(letter)) return drive[1];
      if (!Arrays.asList("C", "D", "Z").contains(letter)
          && !"A".equals(letter)
          && fallback == null) {
        fallback = drive[1];
      }
      if ("A".equals(letter) && fallback == null) fallback = drive[1];
    }
    return fallback;
  }

  public static String getSdCardRootPath() {
    try {
      if (!com.winlator.cmod.feature.stores.steam.utils.PrefManager.INSTANCE
          .getUseExternalStorage()) return null;
      String path =
          com.winlator.cmod.feature.stores.steam.utils.PrefManager.INSTANCE
              .getExternalStoragePath();
      if (path == null || path.isEmpty()) return null;
      File root = new File(path);
      return root.exists() ? root.getAbsolutePath() : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static boolean isOnSdCard(String nativePath) {
    if (nativePath == null || nativePath.isEmpty()) return false;
    String sdCardRoot = getSdCardRootPath();
    if (sdCardRoot == null || sdCardRoot.isEmpty()) return false;
    try {
      String canonicalPath = new File(nativePath).getCanonicalPath();
      String canonicalSdRoot = new File(sdCardRoot).getCanonicalPath();
      return canonicalPath.equals(canonicalSdRoot)
          || canonicalPath.startsWith(canonicalSdRoot + File.separator);
    } catch (IOException e) {
      return nativePath.equals(sdCardRoot) || nativePath.startsWith(sdCardRoot + File.separator);
    }
  }

  public static String getPreferredGameDriveLetter(String nativePath) {
    return isOnSdCard(nativePath) ? "G" : "F";
  }

  public static File ensureDriveCGameSymlink(
      Container container, String source, String gameInstallPath) {
    if (container == null || gameInstallPath == null || gameInstallPath.isEmpty()) return null;

    File gameDir = new File(gameInstallPath);
    if (!gameDir.exists()) return null;
    File canonicalGameDir;
    try {
      canonicalGameDir = gameDir.getCanonicalFile();
    } catch (IOException e) {
      canonicalGameDir = gameDir.getAbsoluteFile();
    }

    String safeSource = source == null || source.isEmpty() ? "Games" : source;
    String gameName = canonicalGameDir.getName();
    if (gameName == null || gameName.isEmpty()) gameName = "Game";
    gameName = gameName.replace("/", "_").replace("\\", "_");

    File parentDir =
        new File(container.getRootDir(), ".wine/drive_c/WinNative/Games/" + safeSource);
    if (!parentDir.exists()) parentDir.mkdirs();

    File link = new File(parentDir, gameName);
    boolean needsCreation = !link.exists() && !isSymlink(link);
    if (!needsCreation) {
      try {
        if (isSymlink(link)) {
          String currentTarget = Files.readSymbolicLink(link.toPath()).toString();
          if (!normalizeHostPath(currentTarget).equals(canonicalGameDir.getPath())) {
            FileUtils.delete(link);
            needsCreation = true;
          }
        }
      } catch (IOException e) {
        if (link.isDirectory()) {
          String[] children = link.list();
          if (children == null || children.length == 0) {
            FileUtils.delete(link);
            needsCreation = true;
          }
        }
      }
    }

    if (needsCreation) FileUtils.symlink(canonicalGameDir.getPath(), link.getAbsolutePath());
    return link;
  }

  public static String getDriveCGameWindowsPath(
      Container container, String source, String gameInstallPath, String nativePath) {
    if (container == null || gameInstallPath == null || nativePath == null) return null;
    try {
      File gameDir = new File(gameInstallPath).getCanonicalFile();
      File target = new File(nativePath).getCanonicalFile();
      String gameDirPath = gameDir.getPath();
      String targetPath = target.getPath();
      if (!targetPath.equals(gameDirPath) && !targetPath.startsWith(gameDirPath + File.separator))
        return null;

      File symlink = ensureDriveCGameSymlink(container, source, gameInstallPath);
      if (symlink == null) return null;

      String relative =
          targetPath.substring(gameDirPath.length()).replace(File.separatorChar, '\\');
      if (relative.isEmpty()) relative = "\\";
      else if (!relative.startsWith("\\")) relative = "\\" + relative;
      return "C:\\WinNative\\Games\\" + source + "\\" + symlink.getName() + relative;
    } catch (IOException e) {
      Log.w("WineUtils", "Failed to resolve C: game path for " + nativePath, e);
      return null;
    }
  }

  public static String getWindowsPath(Container container, String nativePath) {
    return hostPathToMappedWinePath(container, nativePath);
  }

  private static void upsertDrive(List<String[]> entries, String letter, String path) {
    for (String[] entry : entries) {
      if (letter.equals(entry[0])) {
        entry[1] = path;
        return;
      }
    }
    entries.add(new String[] {letter, path});
  }

  private static void removeDrive(List<String[]> entries, String letter) {
    for (int i = entries.size() - 1; i >= 0; i--) {
      if (letter.equals(entries.get(i)[0])) entries.remove(i);
    }
  }

  private static void appendDriveIfPresent(
      StringBuilder builder, List<String[]> entries, String letter) {
    for (String[] entry : entries) {
      if (letter.equals(entry[0])) {
        builder.append(entry[0]).append(':').append(entry[1]);
        return;
      }
    }
  }

  public static void createDosdevicesSymlinks(
      Container container, @Nullable String gameDirectoryPath, boolean exposeSteamGameLink) {
    Log.d(
        "ContainerLaunch",
        "createDosdevicesSymlinks: rootDir="
            + container.getRootDir().getAbsolutePath()
            + " drives="
            + container.getDrives()
            + " gameDir="
            + gameDirectoryPath);
    File dosdevicesDir = new File(container.getRootDir(), ".wine/dosdevices");
    if (!dosdevicesDir.exists()) {
      boolean created = dosdevicesDir.mkdirs();
      Log.d("ContainerLaunch", "createDosdevicesSymlinks: created dosdevices dir=" + created);
    }
    String dosdevicesPath = dosdevicesDir.getPath();
    File[] files = dosdevicesDir.listFiles();
    if (files != null) for (File file : files) if (file.getName().matches("[a-z]:")) file.delete();

    FileUtils.symlink("../drive_c", dosdevicesPath + "/c:");
    FileUtils.symlink(container.getRootDir().getPath() + "/../..", dosdevicesPath + "/z:");

    String packageStorageSuffix = "/com.winnative.cmod/storage";
    String legacyPackageStorageSuffix = "/com.winlator.cmod/storage";
    String packageStoragePath = "/data/data/com.winnative.cmod/storage";
    if (container.getManager() != null && container.getManager().getContext() != null) {
      String packageName = container.getManager().getContext().getPackageName();
      packageStorageSuffix = "/" + packageName + "/storage";
      packageStoragePath = "/data/data/" + packageName + "/storage";
    }

    // Auto-fix containers missing D: and E: drives.
    // IMPORTANT: Only update in-memory drives — do NOT call container.saveData()
    String currentDrives = container.getDrives();
    if (currentDrives != null && (!currentDrives.contains("D:") || !currentDrives.contains("E:"))) {
      Log.d("WineUtils", "Container missing D: or E: drives, appending them...");
      StringBuilder updatedDrives = new StringBuilder(currentDrives);
      if (!currentDrives.contains("D:")) {
        updatedDrives
            .append("D:")
            .append(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS));
      }
      if (!currentDrives.contains("E:")) {
        updatedDrives.append("E:").append(packageStoragePath);
      }
      container.setDrives(updatedDrives.toString());
      Log.d("WineUtils", "Updated container drives (in-memory only) to: " + updatedDrives);
    }

    int driveCount = 0;
    for (String[] drive : container.drivesIterator()) {
      if ("A".equalsIgnoreCase(drive[0])) continue;
      File linkTarget = new File(drive[1]);
      String path = linkTarget.getAbsolutePath();
      boolean isAppStoragePath =
          path.endsWith(packageStorageSuffix) || path.endsWith(legacyPackageStorageSuffix);
      if (!linkTarget.isDirectory() && isAppStoragePath) {
        linkTarget.mkdirs();
        FileUtils.chmod(linkTarget, 0771);
      }
      FileUtils.symlink(path, dosdevicesPath + "/" + drive[0].toLowerCase(Locale.ENGLISH) + ":");
      Log.d("ContainerLaunch", "createDosdevicesSymlinks: " + drive[0] + ": -> " + path);
      driveCount++;
    }
    Log.d("ContainerLaunch", "createDosdevicesSymlinks: created " + driveCount + " drive symlinks");

    // Only expose Steam's steamapps/common symlink for actual Steam launches.
    if (gameDirectoryPath != null && !gameDirectoryPath.isEmpty()) {
      if (exposeSteamGameLink) ensureSteamappsCommonSymlink(container, gameDirectoryPath);
      else removeSteamappsCommonSymlink(container, gameDirectoryPath);
    }
  }

  /**
   * Ensures the steamapps/common/{gameName} symlink exists and points to the correct game install
   * directory. This is critical for ColdClientLoader path resolution, especially when games are
   * installed at custom download paths.
   *
   * <p>- Creates the symlink if it doesn't exist - Recreates if it exists but points to a different
   * (stale) location - Also creates the _CommonRedist and steamapps directory structure
   *
   * @param container The container whose Wine prefix to modify
   * @param gameDirectoryPath The actual path to the game install directory
   */
  public static void ensureSteamappsCommonSymlink(Container container, String gameDirectoryPath) {
    if (gameDirectoryPath == null || gameDirectoryPath.isEmpty()) return;

    File gameDirectory = new File(gameDirectoryPath);
    File canonicalGameDirectory;
    try {
      canonicalGameDirectory = gameDirectory.getCanonicalFile();
    } catch (IOException e) {
      canonicalGameDirectory = gameDirectory.getAbsoluteFile();
    }
    String canonicalGameDirectoryPath = canonicalGameDirectory.getPath();
    String gameName = canonicalGameDirectory.getName();

    // Create C:\Program Files (x86)\Steam\steamapps\common
    File steamCommonDir =
        new File(
            container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/steamapps/common");
    if (!steamCommonDir.exists()) {
      steamCommonDir.mkdirs();
    }

    // Symlink steamapps/common/{gameName} -> actual game directory
    // Always validate the symlink target matches the actual game path (handles custom paths)
    File steamGameLink = new File(steamCommonDir, gameName);
    boolean needsCreation = false;
    if (steamGameLink.exists() || isSymlink(steamGameLink)) {
      // Check if the existing symlink points to the correct location
      try {
        String currentTarget = Files.readSymbolicLink(steamGameLink.toPath()).toString();
        if (!normalizeHostPath(currentTarget).equals(canonicalGameDirectoryPath)) {
          Log.d(
              "WineUtils",
              "Stale Steam symlink detected: "
                  + currentTarget
                  + " (expected "
                  + canonicalGameDirectoryPath
                  + "), recreating");
          steamGameLink.delete();
          needsCreation = true;
        }
        // else: symlink is correct, nothing to do
      } catch (IOException e) {
        // Not a symlink or can't read it - if it's an empty dir, remove and recreate
        String[] children = steamGameLink.list();
        if (steamGameLink.isDirectory() && (children == null || children.length == 0)) {
          steamGameLink.delete();
          needsCreation = true;
        }
        // If it's a non-empty dir, leave it alone (might be a real install)
      }
    } else {
      needsCreation = true;
    }

    if (needsCreation) {
      FileUtils.symlink(canonicalGameDirectoryPath, steamGameLink.getAbsolutePath());
      Log.d(
          "WineUtils",
          "Created Steam game symlink: "
              + steamGameLink
              + " -> "
              + canonicalGameDirectoryPath);
    }

    // Keep Steamworks Shared/_CommonRedist writable inside the Wine prefix.
    // Symlinking to the Android-backed game folder causes Steam's installscript.vdf
    // writes to fail with "disk write error" for shared redistributables.
    File gameCommonRedist = new File(canonicalGameDirectory, "_CommonRedist");
    File steamworksSharedDir = new File(steamCommonDir, "Steamworks Shared");
    if (!steamworksSharedDir.exists()) {
      steamworksSharedDir.mkdirs();
    }
    File steamworksCommonRedist = new File(steamworksSharedDir, "_CommonRedist");
    if (isSymlink(steamworksCommonRedist)) {
      FileUtils.delete(steamworksCommonRedist);
    }
    if (gameCommonRedist.exists() && gameCommonRedist.isDirectory()) {
      FileUtils.copy(gameCommonRedist, steamworksCommonRedist);
    } else if (!steamworksCommonRedist.exists()) {
      steamworksCommonRedist.mkdirs();
    }

    // Ensure steamapps directory exists
    File steamappsDir =
        new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/steamapps");
    if (!steamappsDir.exists()) {
      steamappsDir.mkdirs();
    }
  }

  public static void removeSteamappsCommonSymlink(Container container, String gameDirectoryPath) {
    if (container == null || gameDirectoryPath == null || gameDirectoryPath.isEmpty()) return;

    File gameDirectory = new File(gameDirectoryPath);
    File canonicalGameDirectory;
    try {
      canonicalGameDirectory = gameDirectory.getCanonicalFile();
    } catch (IOException e) {
      canonicalGameDirectory = gameDirectory.getAbsoluteFile();
    }

    File steamCommonDir =
        new File(
            container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/steamapps/common");
    File steamGameLink = new File(steamCommonDir, canonicalGameDirectory.getName());
    if (!steamGameLink.exists() && !isSymlink(steamGameLink)) return;

    try {
      if (isSymlink(steamGameLink)) {
        FileUtils.delete(steamGameLink);
        Log.d("WineUtils", "Removed Steam game symlink for non-Steam launch: " + steamGameLink);
        return;
      }
    } catch (Exception ignored) {
    }

    String[] children = steamGameLink.list();
    if (steamGameLink.isDirectory() && (children == null || children.length == 0)) {
      FileUtils.delete(steamGameLink);
      Log.d("WineUtils", "Removed empty Steam game directory for non-Steam launch: " + steamGameLink);
    }
  }

  /** Checks if a file is a symbolic link without following the link. */
  private static boolean isSymlink(File file) {
    try {
      return Files.isSymbolicLink(file.toPath());
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isRegistryFileValid(File regFile) {
    if (regFile == null || !regFile.isFile() || regFile.length() < 24) return false;
    String contents = FileUtils.readString(regFile);
    if (contents == null) return false;

    String normalized = contents.trim();
    return normalized.startsWith("WINE REGISTRY Version");
  }

  public static boolean isPrefixValid(File containerDir) {
    if (containerDir == null) return false;

    File prefixDir = new File(containerDir, ".wine");
    File systemRegFile = new File(prefixDir, "system.reg");
    File userRegFile = new File(prefixDir, "user.reg");
    File windowsDir = new File(prefixDir, "drive_c/windows");

    return prefixDir.isDirectory()
        && windowsDir.isDirectory()
        && isRegistryFileValid(systemRegFile)
        && isRegistryFileValid(userRegFile);
  }

  private static void setWindowMetrics(WineRegistryEditor registryEditor) {
    byte[] fontNormalData = (new MSLogFont()).toByteArray();
    byte[] fontBoldData = (new MSLogFont()).setWeight(700).toByteArray();
    registryEditor.setHexValue(
        "Control Panel\\Desktop\\WindowMetrics", "CaptionFont", fontBoldData);
    registryEditor.setHexValue("Control Panel\\Desktop\\WindowMetrics", "IconFont", fontNormalData);
    registryEditor.setHexValue("Control Panel\\Desktop\\WindowMetrics", "MenuFont", fontNormalData);
    registryEditor.setHexValue(
        "Control Panel\\Desktop\\WindowMetrics", "MessageFont", fontNormalData);
    registryEditor.setHexValue(
        "Control Panel\\Desktop\\WindowMetrics", "SmCaptionFont", fontNormalData);
    registryEditor.setHexValue(
        "Control Panel\\Desktop\\WindowMetrics", "StatusFont", fontNormalData);
  }

  public static void applySystemTweaks(Context context, WineInfo wineInfo) {
    File rootDir = ImageFs.find(context).getRootDir();
    File systemRegFile = new File(rootDir, ImageFs.WINEPREFIX + "/system.reg");
    File userRegFile = new File(rootDir, ImageFs.WINEPREFIX + "/user.reg");
    File userCacheDir = new File(rootDir, "/home/xuser/.cache");
    if (!userCacheDir.isDirectory()) {
      userCacheDir.mkdirs();
    }
    File userConfigDir = new File(rootDir, "/home/xuser/.config");
    if (!userConfigDir.isDirectory()) {
      userConfigDir.mkdirs();
    }

    try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
      registryEditor.setStringValue("Software\\Classes\\.reg", null, "REGfile");
      registryEditor.setStringValue("Software\\Classes\\.reg", "Content Type", "application/reg");
      registryEditor.setStringValue(
          "Software\\Classes\\REGfile\\Shell\\Open\\command",
          null,
          "C:\\windows\\regedit.exe /C \"%1\"");

      registryEditor.setStringValue(
          "Software\\Classes\\dllfile\\DefaultIcon", null, "shell32.dll,-154");
      registryEditor.setStringValue(
          "Software\\Classes\\lnkfile\\DefaultIcon", null, "shell32.dll,-30");
      registryEditor.setStringValue(
          "Software\\Classes\\inifile\\DefaultIcon", null, "shell32.dll,-151");

      // Set up system fonts if not already done
      File corefontsAddedFile = new File(userConfigDir, "corefonts.added");
      if (!corefontsAddedFile.isFile()) {
        try {
          setupSystemFonts(registryEditor);
          FileUtils.writeString(corefontsAddedFile, String.valueOf(System.currentTimeMillis()));
        } catch (Throwable th) {
          Log.e("WineUtils", "Failed to setup system fonts", th);
        }
      }
    }

    final String[] direct3dLibs = {
      "d3d8",
      "d3d9",
      "d3d10",
      "d3d10_1",
      "d3d10core",
      "d3d11",
      "d3d12",
      "d3d12core",
      "ddraw",
      "dxgi",
      "wined3d"
    };
    // evshim creates SDL virtual joysticks that Wine picks up through winebus,
    // so Wine's builtin dinput/xinput path should stay preferred on all arches.
    final String[] dinputLibs = {"dinput", "dinput8"};
    final String[] xinputLibs = {
      "xinput1_1", "xinput1_2", "xinput1_3", "xinput1_4", "xinput9_1_0", "xinputuap"
    };

    final String dllOverridesKey = "Software\\Wine\\DllOverrides";

    try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
      for (String name : direct3dLibs)
        registryEditor.setStringValue(dllOverridesKey, name, "native,builtin");
      for (String name : dinputLibs)
        registryEditor.setStringValue(dllOverridesKey, name, "builtin,native");
      for (String name : xinputLibs)
        registryEditor.setStringValue(dllOverridesKey, name, "builtin,native");
      // Conditional OpenGL override for ARM64EC (exclude Mali GPUs)
      if (wineInfo != null
          && wineInfo.isArm64EC()
          && !GPUInformation.getRenderer(null, null).contains("Mali")) {
        registryEditor.setStringValue(dllOverridesKey, "opengl32", "native,builtin");
      }
      setWindowMetrics(registryEditor);
    }

    // Copy critical DLLs from wine installation to container
    copyWineDllsToContainer(rootDir, wineInfo);
  }

  /**
   * Copies critical DLLs from the wine installation to the container's system32/syswow64. This
   * ensures games can find user32.dll, shell32.dll, etc. Note: dinput/dinput8 are NOT copied here —
   * they use Wine builtins via builtin,native override.
   */
  private static void copyWineDllsToContainer(File rootDir, WineInfo wineInfo) {
    if (wineInfo == null || wineInfo.path == null || wineInfo.path.isEmpty()) return;
    boolean isArm64EC = wineInfo.isArm64EC();
    File wineSystem32Dir =
        new File(wineInfo.path + "/lib/wine/" + (isArm64EC ? "aarch64-windows" : "x86_64-windows"));
    File wineSysWoW64Dir = new File(wineInfo.path + "/lib/wine/i386-windows");
    File containerSystem32Dir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows/system32");
    File containerSysWoW64Dir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows/syswow64");

    final String[] dlnames = {
      "user32.dll", "shell32.dll",
      "winemenubuilder.exe", "explorer.exe"
    };

    boolean win64 = wineInfo != null && wineInfo.isWin64();
    for (String dlname : dlnames) {
      File src32 = new File(wineSysWoW64Dir, dlname);
      File dst32 = new File(win64 ? containerSysWoW64Dir : containerSystem32Dir, dlname);
      if (src32.exists()) {
        FileUtils.copy(src32, dst32);
      }
      if (win64) {
        File src64 = new File(wineSystem32Dir, dlname);
        File dst64 = new File(containerSystem32Dir, dlname);
        if (src64.exists()) {
          FileUtils.copy(src64, dst64);
        }
      }
    }
  }

  /** Registers core Windows fonts and Wine fonts in the registry. */
  private static void setupSystemFonts(WineRegistryEditor registryEditor) {
    Log.d("WineUtils", "Setting up system fonts");
    String[][] corefonts = {
      {"Andale Mono (TrueType)", "andalemo.ttf"},
      {"Arial (TrueType)", "arial.ttf"},
      {"Arial Black (TrueType)", "ariblk.ttf"},
      {"Arial Bold (TrueType)", "arialbd.ttf"},
      {"Arial Bold Italic (TrueType)", "arialbi.ttf"},
      {"Arial Italic (TrueType)", "ariali.ttf"},
      {"Comic Sans MS (TrueType)", "comic.ttf"},
      {"Comic Sans MS Bold (TrueType)", "comicbd.ttf"},
      {"Courier New (TrueType)", "cour.ttf"},
      {"Courier New Bold (TrueType)", "courbd.ttf"},
      {"Courier New Bold Italic (TrueType)", "courbi.ttf"},
      {"Courier New Italic (TrueType)", "couri.ttf"},
      {"Georgia (TrueType)", "georgia.ttf"},
      {"Georgia Bold (TrueType)", "georgiab.ttf"},
      {"Georgia Bold Italic (TrueType)", "georgiaz.ttf"},
      {"Georgia Italic (TrueType)", "georgiai.ttf"},
      {"Impact (TrueType)", "impact.ttf"},
      {"Times New Roman (TrueType)", "times.ttf"},
      {"Times New Roman Bold (TrueType)", "timesbd.ttf"},
      {"Times New Roman Bold Italic (TrueType)", "timesbi.ttf"},
      {"Times New Roman Italic (TrueType)", "timesi.ttf"},
      {"Trebuchet MS (TrueType)", "trebuc.ttf"},
      {"Trebuchet MS Bold (TrueType)", "trebucbd.ttf"},
      {"Trebuchet MS Bold Italic (TrueType)", "trebucbi.ttf"},
      {"Trebuchet MS Italic (TrueType)", "trebucit.ttf"},
      {"Verdana (TrueType)", "verdana.ttf"},
      {"Verdana Bold (TrueType)", "verdanab.ttf"},
      {"Verdana Bold Italic (TrueType)", "verdanaz.ttf"},
      {"Verdana Italic (TrueType)", "verdanai.ttf"},
      {"Webdings (TrueType)", "webdings.ttf"}
    };
    for (String[] font : corefonts) {
      registryEditor.setStringValue(
          "Software\\Microsoft\\Windows\\CurrentVersion\\Fonts", font[0], font[1]);
      registryEditor.setStringValue(
          "Software\\Microsoft\\Windows NT\\CurrentVersion\\Fonts", font[0], font[1]);
    }

    String[][] wineFonts = {
      {"Marlett (TrueType)", "Z:\\opt\\wine\\share\\wine\\fonts\\marlett.ttf"},
      {"Symbol (TrueType)", "Z:\\opt\\wine\\share\\wine\\fonts\\symbol.ttf"},
      {"Tahoma (TrueType)", "Z:\\opt\\wine\\share\\wine\\fonts\\tahoma.ttf"},
      {"Tahoma Bold (TrueType)", "Z:\\opt\\wine\\share\\wine\\fonts\\tahomabd.ttf"},
      {"Wingdings (TrueType)", "Z:\\opt\\wine\\share\\wine\\fonts\\wingding.ttf"}
    };
    for (String[] font : wineFonts) {
      registryEditor.setStringValue(
          "Software\\Microsoft\\Windows\\CurrentVersion\\Fonts", font[0], font[1]);
      registryEditor.setStringValue(
          "Software\\Microsoft\\Windows NT\\CurrentVersion\\Fonts", font[0], font[1]);
    }
  }

  public static void overrideWinComponentDlls(
      Context context, Container container, String identifier, boolean useNative) {
    final String dllOverridesKey = "Software\\Wine\\DllOverrides";
    File userRegFile = new File(container.getRootDir(), ".wine/user.reg");

    try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
      String wincomponentsStr = FileUtils.readString(context, "wincomponents/wincomponents.json");
      JSONObject wincomponentsJSONObject =
          new JSONObject(wincomponentsStr != null ? wincomponentsStr : "{}");
      JSONArray dlnames = wincomponentsJSONObject.getJSONArray(identifier);
      for (int i = 0; i < dlnames.length(); i++) {
        String dlname = dlnames.getString(i);
        if (useNative) {
          registryEditor.setStringValue(dllOverridesKey, dlname, "native,builtin");
        } else registryEditor.removeValue(dllOverridesKey, dlname);
      }
    } catch (JSONException e) {
    }
  }

  public static void setWinComponentRegistryKeys(
      File systemRegFile, String identifier, boolean useNative, Context context) {
    WineRegistryEditor registryEditor;
    if (identifier.equals("directsound")) {
      registryEditor = new WineRegistryEditor(systemRegFile);
      try {
        if (useNative) {
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{083863F1-70DE-11D0-BD40-00A0C911CE86}\\Instance\\{E30629D1-27E5-11CE-875D-00608CB78066}",
              "CLSID",
              "{E30629D1-27E5-11CE-875D-00608CB78066}");
          registryEditor.setHexValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{083863F1-70DE-11D0-BD40-00A0C911CE86}\\Instance\\{E30629D1-27E5-11CE-875D-00608CB78066}",
              "FilterData",
              "02000000000080000100000000000000307069330200000000000000010000000000000000000000307479330000000038000000480000006175647300001000800000aa00389b710100000000001000800000aa00389b71");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{083863F1-70DE-11D0-BD40-00A0C911CE86}\\Instance\\{E30629D1-27E5-11CE-875D-00608CB78066}",
              "FriendlyName",
              "Wave Audio Renderer");
          registryEditor.setStringValue(
              "Software\\Classes\\CLSID\\{083863F1-70DE-11D0-BD40-00A0C911CE86}\\Instance\\{E30629D1-27E5-11CE-875D-00608CB78066}",
              "CLSID",
              "{E30629D1-27E5-11CE-875D-00608CB78066}");
          registryEditor.setHexValue(
              "Software\\Classes\\CLSID\\{083863F1-70DE-11D0-BD40-00A0C911CE86}\\Instance\\{E30629D1-27E5-11CE-875D-00608CB78066}",
              "FilterData",
              "02000000000080000100000000000000307069330200000000000000010000000000000000000000307479330000000038000000480000006175647300001000800000aa00389b710100000000001000800000aa00389b71");
          registryEditor.setStringValue(
              "Software\\Classes\\CLSID\\{083863F1-70DE-11D0-BD40-00A0C911CE86}\\Instance\\{E30629D1-27E5-11CE-875D-00608CB78066}",
              "FriendlyName",
              "Wave Audio Renderer");
        } else {
          registryEditor.removeKey(
              "Software\\Classes\\Wow6432Node\\CLSID\\{083863F1-70DE-11D0-BD40-00A0C911CE86}\\Instance\\{E30629D1-27E5-11CE-875D-00608CB78066}");
          registryEditor.removeKey(
              "Software\\Classes\\CLSID\\{083863F1-70DE-11D0-BD40-00A0C911CE86}\\Instance\\{E30629D1-27E5-11CE-875D-00608CB78066}");
        }
        registryEditor.close();
      } finally {
      }
    } else if (identifier.equals("xaudio")) {
      registryEditor = new WineRegistryEditor(systemRegFile);
      try {
        if (useNative) {
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{074B110F-7F58-4743-AEA5-12F1B5074ED}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine3_5.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{0977D092-2D95-4E43-8D42-9DDCC2545ED5}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine3_4.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{0AA000AA-F404-11D9-BD7A-0010DC4F8F81}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_0.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{1138472B-D187-44E9-81F2-AE1B0E7785F1}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_3.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{1F1B577E-5E5A-4E8A-BA73-C657EA8E8598}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_1.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{248D8A3B-6256-44D3-A018-2AC96C459F47}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine3_6.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{343E68E6-8F82-4A8D-A2DA-6E9A944B378C}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_9.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{3A2495CE-31D0-435B-8CCF-E9F0843FD960}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_6.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{3B80EE2A-B0F5-4780-9E30-90CB39685B03}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine3_0.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{54B68BC7-3A45-416B-A8C9-19BF19EC1DF5}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_5.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{65D822A4-4799-42C6-9B18-D26CF66DD320}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_10.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{77C56BF4-18A1-42B0-88AF-5072CE814949}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_8.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{94C1AFFA-66E7-4961-9521-CFDEF3128D4F}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine3_3.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{962F5027-99BE-4692-A468-85802CF8DE61}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine3_1.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{BC3E0FC6-2E0D-4C45-BC61-D9C328319BD8}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_4.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{BCC782BC-6492-4C22-8C35-F5D72FE73C6E}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine3_7.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{C60FAE90-4183-4A3F-B2F7-AC1DC49B0E5C}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_2.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{CD0D66EC-8057-43F5-ACBD-66DFB36FD78C}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine2_7.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{D3332F02-3DD0-4DE9-9AEC-20D85C4111B6}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xactengine3_2.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{03219E78-5BC3-44D1-B92E-F63D89CC6526}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_4.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{2139E6DA-C341-4774-9AC3-B4E026347F64}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_5.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{3EDA9B49-2085-498B-9BB2-39A6778493DE}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_6.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{4C5E637A-16C7-4DE3-9C46-5ED22181962D}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_3.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{4C9B6DDE-6809-46E6-A278-9B6A97588670}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_5.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{5A508685-A254-4FBA-9B82-9A24B00306AF}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_7.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{629CF0DE-3ECC-41E7-9926-F7E43EEBEC51}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_2.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{6A93130E-1D53-41D1-A9CF-E758800BB179}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_7.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{8BB7778B-645B-4475-9A73-1DE3170BD3AF}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_4.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{9CAB402C-1D37-44B4-886D-FA4F36170A4C}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_3.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{B802058A-464A-42DB-BC10-B650D6F2586A}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_2.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{C1E3F122-A2EA-442C-854F-20D98F8357A1}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_1.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{C7338B95-52B8-4542-AA79-42EB016C8C1C}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_4.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{CAC1105F-619B-4D04-831A-44E1CBF12D57}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_7.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{CECEC95A-D894-491A-BEE3-5E106FB59F2D}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_6.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{D06DF0D0-8518-441E-822F-5451D5C595B8}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_5.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{E180344B-AC83-4483-959E-18A5C56A5E19}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_3.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{E21A7345-EB21-468E-BE50-804DB97CF708}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_1.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{E48C5A3F-93EF-43BB-A092-2C7CEB946F27}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_6.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{F4769300-B949-4DF9-B333-00D33932E9A6}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_1.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{F5CA7B34-8055-42C0-B836-216129EB7E30}\\InprocServer32",
              null,
              "C:\\windows\\syswow64\\xaudio2_2.dll");
        } else {
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{074B110F-7F58-4743-AEA5-12F1B5074ED}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine3_5.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{0977D092-2D95-4E43-8D42-9DDCC2545ED5}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine3_4.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{0AA000AA-F404-11D9-BD7A-0010DC4F8F81}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_0.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{1138472B-D187-44E9-81F2-AE1B0E7785F1}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_3.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{1F1B577E-5E5A-4E8A-BA73-C657EA8E8598}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_1.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{248D8A3B-6256-44D3-A018-2AC96C459F47}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine3_6.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{343E68E6-8F82-4A8D-A2DA-6E9A944B378C}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_9.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{3A2495CE-31D0-435B-8CCF-E9F0843FD960}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_6.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{3B80EE2A-B0F5-4780-9E30-90CB39685B03}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine3_0.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{54B68BC7-3A45-416B-A8C9-19BF19EC1DF5}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_5.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{65D822A4-4799-42C6-9B18-D26CF66DD320}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_10.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{77C56BF4-18A1-42B0-88AF-5072CE814949}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_8.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{94C1AFFA-66E7-4961-9521-CFDEF3128D4F}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine3_3.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{962F5027-99BE-4692-A468-85802CF8DE61}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine3_1.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{BC3E0FC6-2E0D-4C45-BC61-D9C328319BD8}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_4.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{BCC782BC-6492-4C22-8C35-F5D72FE73C6E}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine3_7.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{C60FAE90-4183-4A3F-B2F7-AC1DC49B0E5C}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_2.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{CD0D66EC-8057-43F5-ACBD-66DFB36FD78C}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine2_7.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{D3332F02-3DD0-4DE9-9AEC-20D85C4111B6}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xactengine3_2.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{03219E78-5BC3-44D1-B92E-F63D89CC6526}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_4.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{2139E6DA-C341-4774-9AC3-B4E026347F64}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_5.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{3EDA9B49-2085-498B-9BB2-39A6778493DE}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_6.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{4C5E637A-16C7-4DE3-9C46-5ED22181962D}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_3.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{4C9B6DDE-6809-46E6-A278-9B6A97588670}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_5.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{5A508685-A254-4FBA-9B82-9A24B00306AF}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_7.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{629CF0DE-3ECC-41E7-9926-F7E43EEBEC51}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_2.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{6A93130E-1D53-41D1-A9CF-E758800BB179}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_7.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{8BB7778B-645B-4475-9A73-1DE3170BD3AF}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_4.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{9CAB402C-1D37-44B4-886D-FA4F36170A4C}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_3.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{B802058A-464A-42DB-BC10-B650D6F2586A}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_2.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{C1E3F122-A2EA-442C-854F-20D98F8357A1}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_1.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{C7338B95-52B8-4542-AA79-42EB016C8C1C}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_4.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{CAC1105F-619B-4D04-831A-44E1CBF12D57}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_7.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{CECEC95A-D894-491A-BEE3-5E106FB59F2D}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_6.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{D06DF0D0-8518-441E-822F-5451D5C595B8}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_5.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{E180344B-AC83-4483-959E-18A5C56A5E19}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_3.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{E21A7345-EB21-468E-BE50-804DB97CF708}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_1.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{E48C5A3F-93EF-43BB-A092-2C7CEB946F27}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_6.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{F4769300-B949-4DF9-B333-00D33932E9A6}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_1.dll");
          registryEditor.setStringValue(
              "Software\\Classes\\Wow6432Node\\CLSID\\{F5CA7B34-8055-42C0-B836-216129EB7E30}\\InprocServer32",
              null,
              "C:\\windows\\system32\\xaudio2_2.dll");
        }
        registryEditor.close();
      } finally {
      }
    }
  }

  public static void changeServicesStatus(Container container, String startupSelection) {
    String[] services = {
      "BITS:3",
      "Eventlog:2",
      "HTTP:3",
      "LanmanServer:3",
      "NDIS:2",
      "PlugPlay:2",
      "RpcSs:3",
      "scardsvr:3",
      "Schedule:3",
      "Spooler:3",
      "StiSvc:3",
      "TermService:3",
      "winebus:2",
      "winehid:2",
      "Winmgmt:3",
      "wuauserv:3"
    };
    String[] aggressiveServices = {
      "BITS:3",
      "Eventlog:2",
      "FontCache:3",
      "FontCache3.0.0.0:3",
      "HTTP:3",
      "LanmanServer:3",
      "MSIServer:3",
      "NDIS:2",
      "nsiproxy:3",
      "PlugPlay:2",
      "RpcSs:3",
      "scardsvr:3",
      "Schedule:3",
      "SharedGpuResources:2",
      "Spooler:3",
      "StiSvc:3",
      "TermService:3",
      "TrkWks:3",
      "W32Time:3",
      "winebus:2",
      "winehid:2",
      "Winmgmt:3",
      "wuauserv:3"
    };
    File systemRegFile = new File(container.getRootDir(), ".wine/system.reg");
    byte selection = 0;
    try {
      selection = Byte.parseByte(startupSelection);
    } catch (NumberFormatException e) {
    }
    WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile);
    try {
      registryEditor.setCreateKeyIfNotExist(false);
      List<String> servicesList = Arrays.asList(services);
      for (String service : aggressiveServices) {
        String name = service.substring(0, service.indexOf(":"));
        int value = Character.getNumericValue(service.charAt(service.length() - 1));
        if (selection == 1) {
          if (servicesList.contains(service)
              && !name.equals("winebus")
              && !name.equals("winehid")
              && !name.equals("PlugPlay")) {
            value = 4;
          }
        } else if (selection == 2
            && !name.equals("winebus")
            && !name.equals("winehid")
            && !name.equals("PlugPlay")) {
          value = 4;
        }
        registryEditor.setDwordValue(
            "System\\CurrentControlSet\\Services\\" + name, "Start", value);
        registryEditor.setDwordValue("System\\ControlSet001\\Services\\" + name, "Start", value);
      }
      registryEditor.close();
    } finally {
    }
  }

  public static void setJoystickRegistryKeys(
      Container container, boolean dinputEnabled, boolean exclusiveXInput) {
    File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
    String value = dinputEnabled ? "override" : "disabled";
    try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
      for (int i = 0; i < 4; i++) {
        if (exclusiveXInput) {
          registryEditor.setStringValue(
              "Software\\Wine\\DirectInput\\Joysticks", "Generic HID Gamepad " + i, value);
          registryEditor.setStringValue(
              "Software\\Wine\\DirectInput\\Joysticks", "ric HID Gamepad " + i, value);
        } else {
          registryEditor.removeValue(
              "Software\\Wine\\DirectInput\\Joysticks", "Generic HID Gamepad " + i);
          registryEditor.removeValue(
              "Software\\Wine\\DirectInput\\Joysticks", "ric HID Gamepad " + i);
        }
      }
    }
  }

  /**
   * Ensures winebus is configured correctly for the fake-input mechanism on every launch. Runs
   * unconditionally so pre-existing containers are repaired. 1. Removes stale WINEBUS device
   * entries (phantom VID_845E devices). 2. Sets DisableHidraw=1 so Proton winebus uses evdev
   * (hooked by libfakeinput).
   */
  public static void ensureWinebusConfig(Container container) {
    File systemRegFile = new File(container.getRootDir(), ".wine/system.reg");
    if (!systemRegFile.exists()) return;

    try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
      // Remove stale WINEBUS device registrations that don't match our fake gamepad
      registryEditor.removeKey("System\\ControlSet001\\Enum\\WINEBUS\\VID_845E&PID_0001", true);
      registryEditor.removeKey("System\\CurrentControlSet\\Enum\\WINEBUS\\VID_845E&PID_0001", true);

      // Ensure winebus parameters disable hidraw and keep evdev enabled
      String winebusParamsKey = "System\\CurrentControlSet\\Services\\winebus\\Parameters";
      registryEditor.setDwordValue(winebusParamsKey, "DisableHidraw", 1);
      registryEditor.setDwordValue(winebusParamsKey, "DisableInput", 0);
      String winebusParamsKey2 = "System\\ControlSet001\\Services\\winebus\\Parameters";
      registryEditor.setDwordValue(winebusParamsKey2, "DisableHidraw", 1);
      registryEditor.setDwordValue(winebusParamsKey2, "DisableInput", 0);
    }
  }

  public static void setJoystickRegistryKeys(File userRegFile, boolean enable) {
    String value = enable ? "override" : "disabled";
    try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
      for (int i = 0; i < 4; i++) {
        registryEditor.setStringValue(
            "Software\\Wine\\DirectInput\\Joysticks", "Generic HID Gamepad " + i, value);
        registryEditor.setStringValue(
            "Software\\Wine\\DirectInput\\Joysticks", "ric HID Gamepad " + i, value);
      }
    }
  }

  public static File getNativePath(ImageFs imageFs, String winPath) {
    if (winPath == null || winPath.isEmpty()) return null;
    String path = winPath.replace("\\", "/");
    if (path.startsWith("\"") && path.endsWith("\"")) path = path.substring(1, path.length() - 1);

    if (path.matches("^[a-zA-Z]:.*")) {
      String drive = path.substring(0, 1).toLowerCase(Locale.ENGLISH);
      String relPath = path.substring(2);
      if (relPath.startsWith("/")) relPath = relPath.substring(1);

      if (drive.equals("c")) {
        return new File(imageFs.getRootDir(), ImageFs.WINEPREFIX + "/drive_c/" + relPath);
      } else {
        File dosdevices = new File(imageFs.getRootDir(), ImageFs.WINEPREFIX + "/dosdevices");
        File link = new File(dosdevices, drive + ":");
        if (link.exists()) {
          try {
            return new File(link.getCanonicalPath(), relPath);
          } catch (Exception e) {
            return new File(link.getAbsolutePath(), relPath);
          }
        }
      }
    }
    return new File(imageFs.getRootDir(), path);
  }

  public static String getDosPath(String path) {
    if (path == null || path.isEmpty()) return "D:\\";
    String downloadsPath =
        android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS)
            .getAbsolutePath();
    String externalStoragePath =
        android.os.Environment.getExternalStorageDirectory().getAbsolutePath();

    if (path.startsWith(downloadsPath)) {
      return "D:" + path.substring(downloadsPath.length()).replace("/", "\\");
    } else if (path.startsWith(externalStoragePath)) {
      return "F:" + path.substring(externalStoragePath.length()).replace("/", "\\");
    }
    return "D:\\"; // fallback
  }
}
