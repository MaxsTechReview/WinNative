package com.winlator.cmod.gamefixes;

import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.gog.data.GOGGame;
import com.winlator.cmod.gog.service.GOGConstants;
import com.winlator.cmod.gog.service.GOGService;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class GameFixes {
    private static final String TAG = "GameFixes";
    private static final String INSTALL_PATH_PLACEHOLDER = "<InstallPath>";
    private static final String GOG_WINDOWS_INSTALL_PATH = "A:\\";

    private static final Map<String, RegistryKeyFix> GOG_FIXES;

    static {
        HashMap<String, RegistryKeyFix> fixes = new HashMap<>();
        fixes.put("1454315831", new RegistryKeyFix(
                "Software\\Wow6432Node\\Bethesda Softworks\\Fallout3",
                Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)
        ));
        fixes.put("1454587428", new RegistryKeyFix(
                "Software\\Wow6432Node\\Bethesda Softworks\\FalloutNV",
                Collections.singletonMap("Installed Path", INSTALL_PATH_PLACEHOLDER)
        ));
        fixes.put("1998527297", new RegistryKeyFix(
                "Software\\Wow6432Node\\Bethesda Softworks\\Fallout4",
                Collections.singletonMap("InstalledPath", INSTALL_PATH_PLACEHOLDER)
        ));
        GOG_FIXES = Collections.unmodifiableMap(fixes);
    }

    private GameFixes() {}

    public static void applyForLaunch(Container container, Shortcut shortcut) {
        if (container == null || shortcut == null) return;
        if (!"GOG".equals(shortcut.getExtra("game_source"))) return;

        String gogId = shortcut.getExtra("gog_id");
        if (gogId.isEmpty()) return;

        RegistryKeyFix fix = GOG_FIXES.get(gogId);
        if (fix == null) return;

        ResolvedPaths resolvedPaths = resolveGogPaths(shortcut, gogId);
        if (resolvedPaths == null) return;

        File systemRegFile = new File(container.getRootDir(), ".wine/system.reg");
        if (!systemRegFile.isFile()) {
            Log.w(TAG, "system.reg missing for container " + container.id + " at " + systemRegFile.getAbsolutePath());
            return;
        }

        fix.apply(systemRegFile, gogId, resolvedPaths.installPathWindows);
    }

    private static ResolvedPaths resolveGogPaths(Shortcut shortcut, String gogId) {
        String shortcutInstallPath = shortcut.getExtra("game_install_path");
        if (isUsableInstallDir(shortcutInstallPath)) {
            return new ResolvedPaths(GOG_WINDOWS_INSTALL_PATH);
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

        return new ResolvedPaths(GOG_WINDOWS_INSTALL_PATH);
    }

    private static boolean isUsableInstallDir(String path) {
        return path != null && !path.isEmpty() && new File(path).isDirectory();
    }

    private static final class ResolvedPaths {
        private final String installPathWindows;

        private ResolvedPaths(String installPathWindows) {
            this.installPathWindows = installPathWindows;
        }
    }

    private static final class RegistryKeyFix {
        private final String registryKey;
        private final Map<String, String> defaultValues;

        private RegistryKeyFix(String registryKey, Map<String, String> defaultValues) {
            this.registryKey = registryKey;
            this.defaultValues = defaultValues;
        }

        private void apply(File systemRegFile, String gameId, String installPathWindows) {
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(systemRegFile)) {
                registryEditor.setCreateKeyIfNotExist(true);
                for (Map.Entry<String, String> entry : defaultValues.entrySet()) {
                    String existingValue = registryEditor.getStringValue(registryKey, entry.getKey(), null);
                    if (existingValue != null && !existingValue.isEmpty()) continue;

                    String value = INSTALL_PATH_PLACEHOLDER.equals(entry.getValue())
                            ? installPathWindows
                            : entry.getValue();
                    registryEditor.setStringValue(registryKey, entry.getKey(), value);
                    Log.d(TAG, "Applied registry fix for game " + gameId + ": " + registryKey + " -> " + entry.getKey());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply registry fix for game " + gameId, e);
            }
        }
    }
}
