package com.winlator.cmod.runtime.compat.gamefixes;

import android.util.Log;
import com.winlator.cmod.feature.stores.gog.data.GOGGame;
import com.winlator.cmod.feature.stores.gog.service.GOGConstants;
import com.winlator.cmod.feature.stores.gog.service.GOGService;
import com.winlator.cmod.runtime.compat.SteamBridge;
import com.winlator.cmod.runtime.compat.gamefixes.helpers.EpicGameFixHelper;
import com.winlator.cmod.runtime.compat.gamefixes.helpers.GogDependencyFixHelper;
import com.winlator.cmod.runtime.container.Container;
import com.winlator.cmod.runtime.container.Shortcut;
import com.winlator.cmod.runtime.wine.WineRegistryEditor;
import com.winlator.cmod.runtime.wine.WineUtils;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GameFixes {
  private static final String TAG = "GameFixes";
  private static final String INSTALL_PATH_PLACEHOLDER = "<InstallPath>";
  private static final Map<String, Fix> GOG_FIXES;
  private static final Map<String, Fix> STEAM_FIXES;
  private static final Map<String, Fix> EPIC_FIXES;

  static {
    HashMap<String, Fix> gogFixes = new HashMap<>();
    gogFixes.put(
        "1454315831",
        new RegistryKeyFix(
            "Software\\Wow6432Node\\Bethesda Softworks\\Fallout3",
            Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)));
    gogFixes.put(
        "1454587428",
        new RegistryKeyFix(
            "Software\\Wow6432Node\\Bethesda Softworks\\FalloutNV",
            Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)));
    gogFixes.put(
        "1998527297",
        new RegistryKeyFix(
            "Software\\Wow6432Node\\Bethesda Softworks\\Fallout4",
            Collections.singletonMap("InstalledPath", INSTALL_PATH_PLACEHOLDER)));
    gogFixes.put(
        "1458058109",
        new RegistryKeyFix(
            "Software\\Wow6432Node\\Bethesda Softworks\\Oblivion",
            Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)));
    gogFixes.put("1589319779", new GogDependencyFix(Arrays.asList("MSVC2017", "MSVC2017_x64")));
    gogFixes.put("2147483047", new GogDependencyFix(Arrays.asList("MSVC2017", "MSVC2017_x64")));
    GOG_FIXES = Collections.unmodifiableMap(gogFixes);

    HashMap<String, Fix> steamFixes = new HashMap<>();
    steamFixes.put(
        "22300",
        new RegistryKeyFix(
            "Software\\Wow6432Node\\Bethesda Softworks\\Fallout3",
            Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)));
    steamFixes.put(
        "22330",
        new RegistryKeyFix(
            "Software\\Wow6432Node\\Bethesda Softworks\\Oblivion",
            Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)));
    steamFixes.put(
        "22380",
        new RegistryKeyFix(
            "Software\\Wow6432Node\\Bethesda Softworks\\FalloutNV",
            Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)));
    STEAM_FIXES = Collections.unmodifiableMap(steamFixes);

    HashMap<String, Fix> epicFixes = new HashMap<>();
    epicFixes.put(
        "b1b4e0b67a044575820cb5e63028dcae",
        new RegistryKeyFix(
            "Software\\Wow6432Node\\Bethesda Softworks\\Fallout3",
            Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)));
    epicFixes.put(
        "59a0c86d02da42e8ba6444cb171e61bf",
        new RegistryKeyFix(
            "Software\\Wow6432Node\\Bethesda Softworks\\Oblivion",
            Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)));
    epicFixes.put(
        "dabb52e328834da7bbe99691e374cb84",
        new RegistryKeyFix(
            "Software\\Wow6432Node\\Bethesda Softworks\\FalloutNV",
            Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)));
    EPIC_FIXES = Collections.unmodifiableMap(epicFixes);
  }

  private GameFixes() {}

  public static void applyForLaunch(Container container, Shortcut shortcut) {
    if (container == null || shortcut == null) return;
    String gameSource = shortcut.getExtra("game_source");

    if ("GOG".equals(gameSource)) {
      applyGogFixes(container, shortcut);
    } else if ("STEAM".equals(gameSource)) {
      applySteamFixes(container, shortcut);
    } else if ("EPIC".equals(gameSource)) {
      applyEpicFixes(container, shortcut);
    }
  }

  private static void applySteamFixes(Container container, Shortcut shortcut) {
    String appId = shortcut.getExtra("app_id");
    if (appId == null || appId.isEmpty()) return;

    Fix fix = STEAM_FIXES.get(appId);
    if (fix == null) return;

    String installPath = SteamBridge.getAppDirPath(Integer.parseInt(appId));
    if (installPath == null || installPath.isEmpty() || !new File(installPath).exists()) {
      Log.d(TAG, "Skipping Steam fix for appId " + appId + " because install path is unavailable");
      return;
    }

    File systemRegFile = new File(container.getRootDir(), ".wine/system.reg");
    String installPathWindows = WineUtils.getDosPath(installPath);
    applyFix(
        fix,
        appId,
        installPath,
        installPathWindows != null ? installPathWindows : "D:\\",
        systemRegFile);
  }

  private static void applyGogFixes(Container container, Shortcut shortcut) {
    String gogId = shortcut.getExtra("gog_id");
    if (gogId == null || gogId.isEmpty()) return;

    Fix fix = GOG_FIXES.get(gogId);
    if (fix == null) return;

    ResolvedPaths resolvedPaths = resolveGogPaths(container, shortcut, gogId);
    if (resolvedPaths == null) return;

    File systemRegFile = new File(container.getRootDir(), ".wine/system.reg");
    applyFix(
        fix, gogId, resolvedPaths.installPath, resolvedPaths.installPathWindows, systemRegFile);
  }

  private static void applyEpicFixes(Container container, Shortcut shortcut) {
    String catalogId = shortcut.getExtra("catalog_id");
    String appIdStr = shortcut.getExtra("app_id");
    if ((catalogId == null || catalogId.isEmpty()) && appIdStr != null) {
      catalogId = EpicGameFixHelper.INSTANCE.getCatalogIdForAppId(appIdStr);
    }
    if (catalogId == null || catalogId.isEmpty()) return;

    Fix fix = EPIC_FIXES.get(catalogId);
    if (fix == null) return;

    String installPath = EpicGameFixHelper.INSTANCE.getInstallPathForCatalog(catalogId);
    if (installPath == null || installPath.isEmpty() || !new File(installPath).exists()) {
      Log.d(
          TAG,
          "Skipping Epic fix for catalogId " + catalogId + " because install path is unavailable");
      return;
    }

    File systemRegFile = new File(container.getRootDir(), ".wine/system.reg");
    String installPathWindows = WineUtils.getDosPath(installPath);
    applyFix(
        fix,
        catalogId,
        installPath,
        installPathWindows != null ? installPathWindows : "D:\\",
        systemRegFile);
  }

  private static void applyFix(
      Fix fix, String gameId, String installPath, String installPathWindows, File systemRegFile) {
    if (fix.requiresSystemReg() && (systemRegFile == null || !systemRegFile.isFile())) {
      if (systemRegFile != null) {
        Log.w(
            TAG,
            "system.reg missing at " + systemRegFile.getAbsolutePath() + " for game " + gameId);
      }
      return;
    }
    try {
      fix.apply(gameId, installPath, installPathWindows, systemRegFile);
    } catch (Exception e) {
      Log.e(TAG, "Failed to apply fix for game " + gameId, e);
    }
  }

  private static ResolvedPaths resolveGogPaths(
      Container container, Shortcut shortcut, String gogId) {
    String shortcutInstallPath = shortcut.getExtra("game_install_path");
    if (isUsableInstallDir(shortcutInstallPath)) {
      String installPathWindows = WineUtils.getDosPath(shortcutInstallPath);
      return new ResolvedPaths(
          shortcutInstallPath, installPathWindows != null ? installPathWindows : "D:\\");
    }

    GOGGame gogGame = GOGService.Companion.getGOGGameOf(gogId);
    if (gogGame == null || !gogGame.isInstalled()) {
      Log.d(TAG, "Skipping GOG fix for " + gogId + " because the game is not installed");
      return null;
    }

    String installPath = gogGame.getInstallPath();
    if (!isUsableInstallDir(installPath) && !gogGame.getTitle().isEmpty()) {
      String defaultInstallPath = GOGConstants.INSTANCE.getGameInstallPath(gogGame.getTitle());
      if (isUsableInstallDir(defaultInstallPath)) {
        installPath = defaultInstallPath;
      }
    }

    if (!isUsableInstallDir(installPath)) {
      Log.w(TAG, "Skipping GOG fix for " + gogId + " because install path is unavailable");
      return null;
    }

    if (!installPath.equals(shortcutInstallPath)) {
      shortcut.putExtra("game_install_path", installPath);
      shortcut.saveData();
    }

    String installPathWindows = WineUtils.getDosPath(installPath);
    return new ResolvedPaths(installPath, installPathWindows != null ? installPathWindows : "D:\\");
  }

  private static boolean isUsableInstallDir(String path) {
    return path != null && !path.isEmpty() && new File(path).isDirectory();
  }

  private static final class ResolvedPaths {
    private final String installPath;
    private final String installPathWindows;

    private ResolvedPaths(String installPath, String installPathWindows) {
      this.installPath = installPath;
      this.installPathWindows = installPathWindows;
    }
  }

  private interface Fix {
    boolean requiresSystemReg();

    void apply(String gameId, String installPath, String installPathWindows, File systemRegFile)
        throws Exception;
  }

  private static final class RegistryKeyFix implements Fix {
    private final String registryKey;
    private final Map<String, String> defaultValues;

    private RegistryKeyFix(String registryKey, Map<String, String> defaultValues) {
      this.registryKey = registryKey;
      this.defaultValues = defaultValues;
    }

    @Override
    public boolean requiresSystemReg() {
      return true;
    }

    @Override
    public void apply(
        String gameId, String installPath, String installPathWindows, File systemRegFile) {
      try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
        registryEditor.setCreateKeyIfNotExist(true);
        for (Map.Entry<String, String> entry : defaultValues.entrySet()) {
          String existingValue = registryEditor.getStringValue(registryKey, entry.getKey(), null);
          if (existingValue != null && !existingValue.isEmpty()) continue;

          String value =
              INSTALL_PATH_PLACEHOLDER.equals(entry.getValue())
                  ? installPathWindows
                  : entry.getValue();
          registryEditor.setStringValue(registryKey, entry.getKey(), value);
          Log.d(
              TAG,
              "Applied registry fix for game "
                  + gameId
                  + ": "
                  + registryKey
                  + " -> "
                  + entry.getKey());
        }
      } catch (Exception e) {
        Log.e(TAG, "Failed to apply registry fix for game " + gameId, e);
      }
    }
  }

  private static final class GogDependencyFix implements Fix {
    private final List<String> dependencyIds;

    private GogDependencyFix(List<String> dependencyIds) {
      this.dependencyIds = dependencyIds;
    }

    @Override
    public boolean requiresSystemReg() {
      return false;
    }

    @Override
    public void apply(
        String gameId, String installPath, String installPathWindows, File systemRegFile) {
      GogDependencyFixHelper.INSTANCE.ensureDependencies(gameId, dependencyIds, installPath);
    }
  }
}
