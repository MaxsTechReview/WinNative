package kr.co.iefriends.pcsx2;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class AssetFiles {
    private AssetFiles() {}

    public static void copyTree(File srcDir, File destDir) {
        File[] kids = srcDir.listFiles();
        if (kids == null) return;
        if (!destDir.isDirectory() && !destDir.mkdirs()) {
            Log.e("ARMSX2", "copyTree: cannot create " + destDir);
            return;
        }
        for (File src : kids) {
            File dest = new File(destDir, src.getName());
            if (src.isDirectory()) {
                copyTree(src, dest);
                continue;
            }
            if (dest.exists() && !isForceFresh(src.getPath())) continue;
            try (InputStream is = new FileInputStream(src); FileOutputStream os = new FileOutputStream(dest)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
            } catch (IOException e) {
                Log.e("ARMSX2", "copyTree failed: " + src + " -> " + dest + ": " + e.getMessage());
            }
        }
    }

    private static boolean isForceFresh(String name) {
        return name.contains("shaders")
                || name.endsWith("GameIndex.yaml")
                || name.endsWith("armsx2_overrides.yaml");
    }

    public static void copyFile(Context p_context, String srcFile, String destFile) {
        AssetManager assetMgr = p_context.getAssets();

        InputStream is = null;
        FileOutputStream os = null;
        final boolean forceFresh = srcFile.contains("shaders")
                || srcFile.endsWith("GameIndex.yaml")
                || srcFile.endsWith("armsx2_overrides.yaml");
        try {
            is = assetMgr.open(srcFile);
            File destFileObj = new File(destFile);
            boolean _exists = destFileObj.exists();
            if (forceFresh) {
                if (_exists && !destFileObj.delete()) {
                    Log.w("ARMSX2", "copyFile: failed to delete stale asset " + destFile);
                }
                _exists = false;
            }
            if (!_exists) {
                os = new FileOutputStream(destFile);

                byte[] buffer = new byte[1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                is.close();
                os.flush();
                os.close();
            }
        } catch (IOException e) {
            Log.e("ARMSX2", "copyFile failed: " + srcFile + " -> " + destFile + ": " + e.getMessage());
            try { if (is != null) is.close(); } catch (IOException ignored) {}
            try { if (os != null) os.close(); } catch (IOException ignored) {}
        }
    }
}
