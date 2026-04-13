package com.winlator.cmod.runtime.display.environment;

import android.content.Context;
import android.util.Log;
import com.winlator.cmod.R;
import com.winlator.cmod.app.config.SettingsConfig;
import com.winlator.cmod.feature.stores.steam.enums.Marker;
import com.winlator.cmod.feature.stores.steam.utils.MarkerUtils;
import com.winlator.cmod.runtime.compat.SteamBridge;
import com.winlator.cmod.runtime.container.Container;
import com.winlator.cmod.runtime.container.ContainerManager;
import com.winlator.cmod.runtime.content.AdrenotoolsManager;
import com.winlator.cmod.runtime.wine.WineInfo;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.io.FileUtils;
import com.winlator.cmod.shared.io.TarCompressorUtils;
import com.winlator.cmod.shared.ui.dialog.DownloadProgressDialog;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ImageFsInstaller {
  public static final byte LATEST_VERSION = 22;

  /**
   * Progress callback for installing ImageFS from assets. Lets callers drive a custom UI (e.g.
   * Jetpack Compose) instead of the legacy dialog. All callbacks are invoked on a background
   * thread; marshal to UI thread as needed.
   */
  public interface ProgressListener {
    void onProgress(int percent);

    void onFinished(boolean success);
  }

  private static void resetContainerImgVersions(Context context) {
    ContainerManager manager = new ContainerManager(context);
    for (Container container : manager.getContainers()) {
      String imgVersion = container.getExtra("imgVersion");
      String wineVersion = container.getWineVersion();
      if (!imgVersion.isEmpty()
          && WineInfo.isMainWineVersion(wineVersion)
          && Short.parseShort(imgVersion) <= 5) {
        container.putExtra("wineprefixNeedsUpdate", "t");
      }

      container.putExtra("imgVersion", null);
      container.saveData();
    }
  }

  public static void installWineFromAssets(final android.app.Activity activity) {
    String[] versions = activity.getResources().getStringArray(R.array.wine_entries);
    File rootDir = ImageFs.find(activity).getRootDir();
    for (String version : versions) {
      File outFile = new File(rootDir, "/opt/" + version);
      outFile.mkdirs();
      TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, activity, version + ".txz", outFile);
    }
  }

  public static void installDriversFromAssets(final android.app.Activity activity) {
    AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(activity);
    String[] adrenotoolsAssetDrivers =
        activity.getResources().getStringArray(R.array.wrapper_graphics_driver_version_entries);

    for (String driver : adrenotoolsAssetDrivers)
      adrenotoolsManager.extractDriverFromResources(driver);
  }

  public static void installFromAssets(final android.app.Activity activity) {
    final DownloadProgressDialog dialog = new DownloadProgressDialog(activity);
    dialog.show(R.string.setup_wizard_installing_system_files);
    installFromAssets(
        activity,
        new ProgressListener() {
          @Override
          public void onProgress(int percent) {
            activity.runOnUiThread(() -> dialog.setProgress(percent));
          }

          @Override
          public void onFinished(boolean success) {
            dialog.closeOnUiThread();
          }
        });
  }

  public static void installFromAssets(
      final android.app.Activity activity, final ProgressListener listener) {
    AppUtils.keepScreenOn(activity);
    ImageFs imageFs = ImageFs.find(activity);
    File rootDir = imageFs.getRootDir();

    SettingsConfig.resetEmulatorsVersion(activity);

    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              clearRootDir(rootDir);
              final byte compressionRatio = 22;
              final long contentLength =
                  (long) (FileUtils.getSize(activity, "imagefs.txz") * (100.0f / compressionRatio));
              AtomicLong totalSizeRef = new AtomicLong();

              boolean success =
                  TarCompressorUtils.extract(
                      TarCompressorUtils.Type.XZ,
                      activity,
                      "imagefs.txz",
                      rootDir,
                      (file, size) -> {
                        if (size > 0) {
                          long totalSize = totalSizeRef.addAndGet(size);
                          final int progress = (int) (((float) totalSize / contentLength) * 100);
                          if (listener != null) listener.onProgress(progress);
                        }
                        return file;
                      });

              if (success) {
                ExecutorService pool = Executors.newFixedThreadPool(3);
                CountDownLatch latch = new CountDownLatch(3);
                pool.execute(
                    () -> {
                      try {
                        installWineFromAssets(activity);
                      } finally {
                        latch.countDown();
                      }
                    });
                pool.execute(
                    () -> {
                      try {
                        installDriversFromAssets(activity);
                      } finally {
                        latch.countDown();
                      }
                    });
                pool.execute(
                    () -> {
                      try {
                        installGuestExtras(activity, rootDir);
                      } finally {
                        latch.countDown();
                      }
                    });
                try {
                  latch.await();
                } catch (InterruptedException ignored) {
                }
                pool.shutdown();
                imageFs.createImgVersionFile(LATEST_VERSION);
                resetContainerImgVersions(activity);
              } else
                AppUtils.showToast(activity, R.string.setup_wizard_unable_to_install_system_files);

              if (listener != null) listener.onFinished(success);
            });
  }

  public static void installIfNeeded(final android.app.Activity activity) {
    ImageFs imageFs = ImageFs.find(activity);
    if (!imageFs.isValid() || imageFs.getVersion() < LATEST_VERSION) installFromAssets(activity);
  }

  /**
   * Version that works from any Activity (e.g. UnifiedActivity). Shows a simple progress dialog and
   * installs ImageFS from assets if needed.
   */
  public static void installIfNeededFromAny(final android.app.Activity activity) {
    ImageFs imageFs = ImageFs.find(activity);
    if (imageFs.isValid() && imageFs.getVersion() >= LATEST_VERSION) return;

    final DownloadProgressDialog dialog = new DownloadProgressDialog(activity);
    activity.runOnUiThread(() -> dialog.show(R.string.setup_wizard_installing_system_files));

    File rootDir = imageFs.getRootDir();
    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              clearRootDir(rootDir);
              final byte compressionRatio = 22;
              final long contentLength =
                  (long) (FileUtils.getSize(activity, "imagefs.txz") * (100.0f / compressionRatio));
              AtomicLong totalSizeRef = new AtomicLong();

              boolean success =
                  TarCompressorUtils.extract(
                      TarCompressorUtils.Type.XZ,
                      activity,
                      "imagefs.txz",
                      rootDir,
                      (file, size) -> {
                        if (size > 0) {
                          long totalSize = totalSizeRef.addAndGet(size);
                          final int progress = (int) (((float) totalSize / contentLength) * 100);
                          activity.runOnUiThread(() -> dialog.setProgress(progress));
                        }
                        return file;
                      });

              if (success) {
                ExecutorService pool = Executors.newFixedThreadPool(2);
                CountDownLatch latch = new CountDownLatch(2);
                pool.execute(
                    () -> {
                      try {
                        String[] versions =
                            activity.getResources().getStringArray(R.array.wine_entries);
                        for (String version : versions) {
                          File outFile = new File(rootDir, "/opt/" + version);
                          outFile.mkdirs();
                          TarCompressorUtils.extract(
                              TarCompressorUtils.Type.XZ, activity, version + ".txz", outFile);
                        }
                      } catch (Exception e) {
                        /* wine assets may not exist */
                      } finally {
                        latch.countDown();
                      }
                    });
                pool.execute(
                    () -> {
                      try {
                        installGuestExtras(activity, rootDir);
                      } finally {
                        latch.countDown();
                      }
                    });
                try {
                  latch.await();
                } catch (InterruptedException ignored) {
                }
                pool.shutdown();
                clearSteamDllMarkers(activity);
                imageFs.createImgVersionFile(LATEST_VERSION);
              } else {
                activity.runOnUiThread(
                    () ->
                        AppUtils.showToast(
                            activity,
                            R.string.setup_wizard_unable_to_install_system_files,
                            android.widget.Toast.LENGTH_LONG));
              }

              dialog.closeOnUiThread();
            });
  }

  private static void clearOptDir(File optDir) {
    File[] files = optDir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.getName().equals("installed-wine")) continue;
        FileUtils.delete(file);
      }
    }
  }

  private static void clearRootDir(File rootDir) {
    if (rootDir.isDirectory()) {
      File[] files = rootDir.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            String name = file.getName();
            if (name.equals("home")) {
              continue;
            }
          }
          FileUtils.delete(file);
        }
      }
    } else rootDir.mkdirs();
  }

  private static void installGuestExtras(Context context, File rootDir) {
    try {
      TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "redirect.tzst", rootDir);
    } catch (Exception e) {
      Log.w(
          "ImageFsInstaller",
          "redirect.tzst not found or failed to extract; continuing without redirect libs");
    }

    try {
      TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "extras.tzst", rootDir);
    } catch (Exception e) {
      Log.w(
          "ImageFsInstaller",
          "extras.tzst not found or failed to extract; Steamless assets may be missing");
      return;
    }

    chmodIfExists(new File(rootDir, "generate_interfaces_file.exe"));
    chmodIfExists(new File(rootDir, "Steamless/Steamless.CLI.exe"));
    // chmod any Mono MSI that was bundled in extras.tzst
    File monoDir = new File(rootDir, "opt/mono-gecko-offline");
    if (monoDir.isDirectory()) {
      File[] msiFiles = monoDir.listFiles();
      if (msiFiles != null) {
        for (File msi : msiFiles) {
          if (msi.getName().startsWith("wine-mono-") && msi.getName().endsWith("-x86.msi")) {
            chmodIfExists(msi);
          }
        }
      }
    }
    chmodIfExists(new File(rootDir, "usr/lib/libredirect.so"));
    chmodIfExists(new File(rootDir, "usr/lib/libredirect-bionic.so"));
  }

  private static void chmodIfExists(File file) {
    if (file.exists()) {
      FileUtils.chmod(file, 0755);
    }
  }

  /**
   * Remove Steam DLL state markers after reinstalling ImageFS so future launches re-apply
   * replacements when needed.
   */
  private static void clearSteamDllMarkers(Context context) {
    try {
      ContainerManager manager = new ContainerManager(context);
      for (Container container : manager.getContainers()) {
        try {
          int gameId = container.id;
          String appDirPath = SteamBridge.getAppDirPath(gameId);
          MarkerUtils.INSTANCE.removeMarker(appDirPath, Marker.STEAM_DLL_REPLACED);
          MarkerUtils.INSTANCE.removeMarker(appDirPath, Marker.STEAM_DLL_RESTORED);
          MarkerUtils.INSTANCE.removeMarker(appDirPath, Marker.STEAM_COLDCLIENT_USED);
          MarkerUtils.INSTANCE.removeMarker(appDirPath, Marker.STEAM_DRM_PATCHED);
          Log.i(
              "ImageFsInstaller",
              "Cleared Steam markers for container "
                  + container.getName()
                  + " (ID: "
                  + container.id
                  + ")");
        } catch (Exception e) {
          Log.w("ImageFsInstaller", "Failed to clear markers for container ID " + container.id, e);
        }
      }
    } catch (Exception e) {
      Log.e("ImageFsInstaller", "Error clearing Steam DLL markers", e);
    }
  }
}
