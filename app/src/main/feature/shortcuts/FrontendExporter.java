package com.winlator.cmod.feature.shortcuts;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.app.config.SettingsConfig;
import com.winlator.cmod.runtime.container.ContainerManager;
import com.winlator.cmod.runtime.container.Shortcut;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.File;
import java.util.ArrayList;

/**
 * Writes shortcuts as standalone .desktop files (plus their icon) into the configured export
 * folder so external game frontends (ES-DE, Daijisho, etc.) can scan and launch them. The
 * exported file keeps the uuid/container_id in [Extra Data] that XServerDisplayActivity resolves
 * a game from, and points Icon= at a copied PNG so the frontend can render cover art.
 */
public final class FrontendExporter {
  private static final String TAG = "FrontendExporter";

  private FrontendExporter() {}

  /** Resolve (and create) the configured export directory, or null if it can't be written. */
  public static File resolveExportDir(Context context) {
    if (context == null) return null;
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String uriString = prefs.getString("shortcuts_export_path_uri", null);
    File dir;
    if (uriString != null) {
      String resolved = FileUtils.getFilePathFromUri(context, Uri.parse(uriString));
      if (resolved == null || resolved.isEmpty()) return null;
      dir = new File(resolved);
    } else {
      dir = new File(SettingsConfig.DEFAULT_SHORTCUT_EXPORT_PATH);
    }
    if (!dir.exists() && !dir.mkdirs()) return null;
    return dir;
  }

  /** Export a single shortcut to the configured folder. Returns the written file, or null. */
  public static File exportOne(Context context, Shortcut shortcut) {
    File dir = resolveExportDir(context);
    if (dir == null) return null;
    return exportOne(context, shortcut, dir);
  }

  /** Export a single shortcut into {@code dir}. Returns the written .desktop file, or null. */
  public static File exportOne(Context context, Shortcut shortcut, File dir) {
    if (dir == null || shortcut == null || shortcut.file == null || !shortcut.file.isFile()) {
      return null;
    }
    try {
      // Frontends resolve our game by uuid / container_id, so make sure both are persisted first.
      shortcut.genUUID();
      if (shortcut.getExtra("container_id").isEmpty()) {
        shortcut.putExtra("container_id", String.valueOf(shortcut.container.id));
        shortcut.saveData();
      }

      String baseName = FileUtils.getBasename(shortcut.file.getPath());

      // Copy the cover/icon next to the .desktop so the frontend can show art.
      String iconPath = null;
      File iconSrc = resolveIconFile(shortcut);
      if (iconSrc != null && iconSrc.isFile()) {
        File iconDst = new File(dir, baseName + ".png");
        FileUtils.copy(iconSrc, iconDst);
        iconPath = iconDst.getAbsolutePath();
      }

      File out = new File(dir, shortcut.file.getName());
      FileUtils.writeString(out, buildDesktopContent(shortcut.file, iconPath));
      return out;
    } catch (Exception e) {
      Log.e(TAG, "Failed to export shortcut: " + shortcut.name, e);
      return null;
    }
  }

  /** Export every shortcut across all containers. Returns the number exported. */
  public static int exportAll(Context context) {
    File dir = resolveExportDir(context);
    if (dir == null) return 0;
    ArrayList<Shortcut> shortcuts;
    try {
      shortcuts = new ContainerManager(context).loadShortcuts();
    } catch (Exception e) {
      Log.e(TAG, "Failed to load shortcuts for export", e);
      return 0;
    }
    int count = 0;
    for (Shortcut shortcut : shortcuts) {
      if (exportOne(context, shortcut, dir) != null) count++;
    }
    return count;
  }

  private static File resolveIconFile(Shortcut shortcut) {
    String[] candidates = {
      shortcut.getExtra("customLibraryIconPath"),
      shortcut.getCustomCoverArtPath(),
      shortcut.getExtra("customCoverArtPath"),
    };
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isEmpty()) {
        File file = new File(candidate);
        if (file.isFile()) return file;
      }
    }
    if (shortcut.iconFile != null && shortcut.iconFile.isFile()) return shortcut.iconFile;
    return null;
  }

  // Faithful copy of the source .desktop, but with the Icon line normalized to the exported PNG
  // so the frontend can render it. uuid / container_id already live in [Extra Data].
  private static String buildDesktopContent(File source, String iconPath) {
    StringBuilder out = new StringBuilder();
    boolean inExtra = false;
    for (String line : FileUtils.readLines(source)) {
      String trimmed = line.trim();
      if (trimmed.startsWith("[")) {
        inExtra = trimmed.equals("[Extra Data]");
        out.append(line).append("\n");
        if (!inExtra && trimmed.equals("[Desktop Entry]") && iconPath != null) {
          out.append("Icon=").append(iconPath).append("\n");
        }
        continue;
      }
      // Drop the original Icon line in [Desktop Entry]; ours was re-emitted under the header.
      if (!inExtra && iconPath != null && trimmed.startsWith("Icon=")) continue;
      out.append(line).append("\n");
    }
    return out.toString();
  }
}
