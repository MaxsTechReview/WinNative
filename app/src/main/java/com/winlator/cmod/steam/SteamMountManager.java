package com.winlator.cmod.steam;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.ImageFs;
import com.winlator.cmod.core.Container;

import java.io.File;

public class SteamMountManager {
    private static final String TAG = "SteamMountManager";
    private static final String STEAM_PATH_IN_WINE = ".wine/drive_c/Program Files (x86)/Steam";

    public static File getSharedSteamDir(Context context) {
        ContentProfile profile = new ContentsManager(context).getProfileByEntryName("steam");
        if (profile == null) return null;
        return ContentsManager.getInstallDir(context, profile);
    }

    public static File getAccessibleSteamDir(Context context, Container container) {
        File sharedDir = getSharedSteamDir(context);
        if (sharedDir != null && sharedDir.exists()) return sharedDir;
        return new File(container.getRootDir(), STEAM_PATH_IN_WINE);
    }

    public static boolean mountSharedSteam(Context context, Container container) {
        File sharedDir = getSharedSteamDir(context);
        if (sharedDir == null || !sharedDir.exists()) return false;

        File mountPoint = new File(container.getRootDir(), STEAM_PATH_IN_WINE);
        mountPoint.getParentFile().mkdirs();

        if (mountPoint.exists()) {
            if (mountPoint.isDirectory() && !FileUtils.isSymlink(mountPoint)) {
                File backup = new File(mountPoint.getParentFile(), "Steam.local_backup");
                if (!backup.exists()) mountPoint.renameTo(backup);
                else FileUtils.delete(mountPoint);
            } else {
                FileUtils.delete(mountPoint);
            }
        }

        boolean success = FileUtils.symlink(sharedDir.getAbsolutePath(), mountPoint.getAbsolutePath());
        if (success) Log.d(TAG, "Mounted shared Steam runtime for container: " + container.getName());
        return success;
    }

    public static void unmountSharedSteam(Container container) {
        File mountPoint = new File(container.getRootDir(), STEAM_PATH_IN_WINE);
        if (FileUtils.isSymlink(mountPoint)) {
            FileUtils.delete(mountPoint);
            Log.d(TAG, "Unmounted shared Steam runtime from container: " + container.getName());
        }
    }
}
