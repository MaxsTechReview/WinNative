package com.winlator.cmod.shared.io;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.github.luben.zstd.ZstdOutputStreamNoFinalizer;
import com.winlator.cmod.shared.util.OnExtractFileListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

public abstract class TarCompressorUtils {
  private static final String TAG = "TarCompressor";
  private static final ExecutorService EXTRACTION_EXECUTOR =
      Executors.newFixedThreadPool(
          Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));

  public enum Type {
    XZ,
    ZSTD
  }

  // Interface to define the exclusion filter
  public interface ExclusionFilter {
    boolean shouldInclude(File file);
  }

  public static Future<Boolean> extractAsync(
      Type type, Context context, String assetFile, File destination) {
    return extractAsync(type, context, assetFile, destination, null);
  }

  public static Future<Boolean> extractAsync(
      Type type,
      Context context,
      String assetFile,
      File destination,
      OnExtractFileListener onExtractFileListener) {
    return submitExtraction(
        () -> extract(type, context, assetFile, destination, onExtractFileListener));
  }

  public static Future<Boolean> extractAsync(
      Type type, Context context, Uri source, File destination) {
    return extractAsync(type, context, source, destination, null);
  }

  public static Future<Boolean> extractAsync(
      Type type,
      Context context,
      Uri source,
      File destination,
      OnExtractFileListener onExtractFileListener) {
    return submitExtraction(
        () -> extract(type, context, source, destination, onExtractFileListener));
  }

  public static Future<Boolean> extractAsync(Type type, File source, File destination) {
    return extractAsync(type, source, destination, null);
  }

  public static Future<Boolean> extractAsync(
      Type type, File source, File destination, OnExtractFileListener onExtractFileListener) {
    return submitExtraction(() -> extract(type, source, destination, onExtractFileListener));
  }

  private static Future<Boolean> submitExtraction(Callable<Boolean> task) {
    return EXTRACTION_EXECUTOR.submit(task);
  }

  private static void addFile(ArchiveOutputStream tar, File file, String entryName) {
    try {
      tar.putArchiveEntry(tar.createArchiveEntry(file, entryName));
      try (BufferedInputStream inStream =
          new BufferedInputStream(new FileInputStream(file), StreamUtils.BUFFER_SIZE)) {
        StreamUtils.copy(inStream, tar);
      }
      tar.closeArchiveEntry();
    } catch (Exception e) {
    }
  }

  private static void addLinkFile(ArchiveOutputStream tar, File file, String entryName) {
    try {
      TarArchiveEntry entry = new TarArchiveEntry(entryName, TarConstants.LF_SYMLINK);
      entry.setLinkName(FileUtils.readSymlink(file));
      tar.putArchiveEntry(entry);
      tar.closeArchiveEntry();
    } catch (Exception e) {
    }
  }

  private static void addDirectory(
      ArchiveOutputStream tar, File folder, String basePath, ExclusionFilter filter)
      throws IOException {
    File[] files = folder.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (filter != null && !filter.shouldInclude(file)) {
        continue; // Skip files that should be excluded
      }
      if (FileUtils.isSymlink(file)) {
        addLinkFile(tar, file, basePath + file.getName());
      } else if (file.isDirectory()) {
        String entryName = basePath + file.getName() + "/";
        tar.putArchiveEntry(tar.createArchiveEntry(folder, entryName));
        tar.closeArchiveEntry();
        addDirectory(tar, file, entryName, filter);
      } else {
        addFile(tar, file, basePath + file.getName());
      }
    }
  }

  public static void compress(Type type, File file, File destination, int level) {
    compress(type, new File[] {file}, destination, level, null);
  }

  public static void compress(
      Type type, File file, File destination, int level, ExclusionFilter filter) {
    compress(type, new File[] {file}, destination, level, filter);
  }

  public static void compress(
      Type type, File[] files, File destination, int level, ExclusionFilter filter) {
    try (OutputStream outStream = getCompressorOutputStream(type, destination, level);
        TarArchiveOutputStream tar = new TarArchiveOutputStream(outStream)) {
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
      for (File file : files) {
        if (filter != null && !filter.shouldInclude(file)) {
          continue; // Skip files that should be excluded
        }
        if (FileUtils.isSymlink(file)) {
          addLinkFile(tar, file, file.getName());
        } else if (file.isDirectory()) {
          String basePath = file.getName() + "/";
          tar.putArchiveEntry(tar.createArchiveEntry(file, basePath));
          tar.closeArchiveEntry();
          addDirectory(tar, file, basePath, filter);
        } else {
          addFile(tar, file, file.getName());
        }
      }
      tar.finish();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static boolean extract(Type type, Context context, String assetFile, File destination) {
    return extract(type, context, assetFile, destination, null);
  }

  public static boolean extract(
      Type type,
      Context context,
      String assetFile,
      File destination,
      OnExtractFileListener onExtractFileListener) {
    if (context == null || assetFile == null || destination == null) return false;
    int nativeType = toNativeType(type);
    try {
      if (NativeContentIO.extractAsset(
          nativeType, context.getAssets(), assetFile, destination, onExtractFileListener)) {
        return true;
      }
      Log.e(TAG, "Native asset extraction failed: " + assetFile);
      return false;
    } catch (Throwable e) {
      Log.e(TAG, "Native asset extraction failed: " + assetFile, e);
      return false;
    }
  }

  public static boolean extract(Type type, Context context, Uri source, File destination) {
    return extract(type, context, source, destination, null);
  }

  public static boolean extract(
      Type type,
      Context context,
      Uri source,
      File destination,
      OnExtractFileListener onExtractFileListener) {
    if (context == null || source == null || destination == null) return false;
    try {
      String scheme = source.getScheme();
      if (source.toString().startsWith("/")
          || scheme == null
          || scheme.isEmpty()
          || "file".equalsIgnoreCase(scheme)) {
        String filePath = source.getPath();
        if (filePath == null || filePath.isEmpty()) {
          filePath = source.toString();
        }
        File sourceFile = new File(filePath);
        if (sourceFile.isFile()) {
          return extract(type, sourceFile, destination, onExtractFileListener);
        }
      }

      String resolvedPath = FileUtils.getFilePathFromUri(context, source);
      if (resolvedPath != null && !resolvedPath.isEmpty()) {
        File sourceFile = new File(resolvedPath);
        if (sourceFile.isFile()) {
          return extract(type, sourceFile, destination, onExtractFileListener);
        }
      }

      File stagedArchive = stageUriArchive(context, source, type);
      if (stagedArchive == null) return false;
      try {
        return extract(type, stagedArchive, destination, onExtractFileListener);
      } finally {
        if (!stagedArchive.delete() && stagedArchive.exists()) {
          Log.w(TAG, "Unable to delete staged archive: " + stagedArchive);
        }
      }
    } catch (FileNotFoundException e) {
      return false;
    } catch (Throwable e) {
      Log.e(TAG, "Native URI extraction failed for " + source, e);
      return false;
    }
  }

  public static boolean extract(Type type, File source, File destination) {
    return extract(type, source, destination, null);
  }

  public static boolean extract(
      Type type, File source, File destination, OnExtractFileListener onExtractFileListener) {
    if (source == null || destination == null || !source.isFile()) return false;
    int nativeType = toNativeType(type);
    try {
      return NativeContentIO.extractArchive(nativeType, source, destination, onExtractFileListener);
    } catch (Throwable e) {
      Log.e(TAG, "Native extraction failed for " + source, e);
      return false;
    }
  }

  private static int toNativeType(Type type) {
    return type == Type.XZ ? NativeContentIO.TYPE_XZ : NativeContentIO.TYPE_ZSTD;
  }

  private static File stageUriArchive(Context context, Uri source, Type type) throws IOException {
    File stagingDir = new File(context.getCacheDir(), "native-archive-stage");
    if (!stagingDir.isDirectory() && !stagingDir.mkdirs()) return null;

    File stagedArchive =
        FileUtils.createTempFile(stagingDir, "archive-" + type.name().toLowerCase(Locale.ROOT));
    try (InputStream inputStream = context.getContentResolver().openInputStream(source)) {
      if (inputStream == null) {
        stagedArchive.delete();
        return null;
      }
      try (BufferedInputStream inStream =
              new BufferedInputStream(inputStream, StreamUtils.BUFFER_SIZE);
          BufferedOutputStream outStream =
              new BufferedOutputStream(
                  new FileOutputStream(stagedArchive), StreamUtils.BUFFER_SIZE)) {
        if (!StreamUtils.copy(inStream, outStream)) {
          stagedArchive.delete();
          return null;
        }
      }
    } catch (IOException e) {
      stagedArchive.delete();
      throw e;
    }
    return stagedArchive;
  }

  private static void applyExtractedEntryPermissions(File file, TarArchiveEntry entry) {
    if (entry == null || file == null) return;
    if (entry.isDirectory()) {
      FileUtils.chmod(file, 0771);
      return;
    }
    if (entry.isSymbolicLink()) {
      return;
    }

    // Only regular files marked executable in the archive need an explicit chmod.
    if ((entry.getMode() & 0111) != 0) {
      FileUtils.chmod(file, 0771);
    }
  }

  private static OutputStream getCompressorOutputStream(Type type, File destination, int level)
      throws IOException {
    if (type == Type.XZ) {
      return new XZCompressorOutputStream(
          new BufferedOutputStream(new FileOutputStream(destination), StreamUtils.BUFFER_SIZE),
          level);
    } else if (type == Type.ZSTD) {
      return new ZstdOutputStreamNoFinalizer(
          new BufferedOutputStream(new FileOutputStream(destination), StreamUtils.BUFFER_SIZE),
          level);
    }
    return null;
  }

  public static void archive(File[] files, File destination, ExclusionFilter filter) {
    try (OutputStream outStream =
            new BufferedOutputStream(new FileOutputStream(destination), StreamUtils.BUFFER_SIZE);
        TarArchiveOutputStream tar = new TarArchiveOutputStream(outStream)) {
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
      for (File file : files) {
        if (filter != null && !filter.shouldInclude(file)) {
          continue; // Skip files that should be excluded
        }
        if (FileUtils.isSymlink(file)) {
          addLinkFile(tar, file, file.getName());
        } else if (file.isDirectory()) {
          String basePath = file.getName() + "/";
          tar.putArchiveEntry(tar.createArchiveEntry(file, basePath));
          tar.closeArchiveEntry();
          addDirectory(tar, file, basePath, filter);
        } else {
          addFile(tar, file, file.getName());
        }
      }
      tar.finish();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static boolean extractTar(
      File source, File destination, OnExtractFileListener onExtractFileListener) {
    if (source == null || !source.isFile()) return false;
    try (InputStream inStream =
            new BufferedInputStream(new FileInputStream(source), StreamUtils.BUFFER_SIZE);
        TarArchiveInputStream tar = new TarArchiveInputStream(inStream)) {
      TarArchiveEntry entry;
      String topLevelDirectory = null;
      while ((entry = (TarArchiveEntry) tar.getNextEntry()) != null) {
        if (!tar.canReadEntryData(entry)) continue;

        // Get the top-level directory name
        String entryName = entry.getName();
        if (topLevelDirectory == null) {
          if (entry.isDirectory()) {
            topLevelDirectory = entryName;
            continue; // Skip creating the top-level directory
          }
        }

        // Skip the entire tmp directory
        if (entryName.contains("/tmp/")) {
          Log.d("RestoreOp", "Skipping tmp directory: " + entryName);
          continue;
        }

        // Adjust the extraction path to remove the top-level directory
        String adjustedName = entryName.replaceFirst("^" + topLevelDirectory, "");
        File file = new File(destination, adjustedName);

        if (onExtractFileListener != null) {
          file = onExtractFileListener.onExtractFile(file, entry.getSize());
          if (file == null) continue;
        }

        if (entry.isDirectory()) {
          if (!file.isDirectory()) file.mkdirs();
        } else {
          if (entry.isSymbolicLink()) {
            FileUtils.symlink(entry.getLinkName(), file.getAbsolutePath());
          } else {
            try (BufferedOutputStream outStream =
                new BufferedOutputStream(new FileOutputStream(file), StreamUtils.BUFFER_SIZE)) {
              if (!StreamUtils.copy(tar, outStream)) return false;
            }
            if (onExtractFileListener != null) {
              onExtractFileListener.onExtractFileProgress(file, entry.getSize());
            }
          }
        }

        applyExtractedEntryPermissions(file, entry);
      }
      return true;
    } catch (IOException e) {
      Log.e("RestoreOp", "Failed to extract tar file", e);
      return false;
    }
  }
}
