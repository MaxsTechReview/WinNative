package com.winlator.cmod.runtime.content;

/**
 * Shared download helper with WinNative.dev-first resolution.
 *
 * <p>On first use the class loads a cached filename → full-URL map from app storage when it is
 * still fresh enough; otherwise it recursively crawls https://WinNative.dev/Downloads/ and every
 * subdirectory it finds to rebuild that map. When any download is requested, the filename from the
 * original (GitHub) URL is looked up in the map. If the file exists on WinNative.dev the download
 * is attempted from there first; only if it fails does it fall back to the original URL.
 */
import android.content.Context;
import android.util.Log;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.app.PluviaApp;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

public class Downloader {

  private static final String TAG = "Downloader";
  private static final String WINNATIVE_ROOT = "https://WinNative.dev/Downloads/";
  private static final int MAX_CRAWL_DEPTH = 10;
  private static final int READ_BUFFER_SIZE = 256 * 1024; // 256 KB — fewer syscalls per read cycle

  /**
   * Shared OkHttpClient for file downloads. - HTTP/2 + connection pooling so the 2nd+ downloads to
   * the same host skip TCP handshake and slow-start entirely - Connections stay warm for 5 minutes
   * across sequential component installs
   */
  private static final OkHttpClient FILE_CLIENT =
      new OkHttpClient.Builder()
          .connectTimeout(15, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .connectionPool(new ConnectionPool(8, 5, TimeUnit.MINUTES))
          .retryOnConnectionFailure(true)
          .followRedirects(true)
          .followSslRedirects(true)
          .build();

  /**
   * Lightweight client for short string fetches (JSON, directory listings). Shares the same
   * connection pool as file downloads so that a directory crawl warms up connections for subsequent
   * file downloads.
   */
  private static final OkHttpClient STRING_CLIENT =
      FILE_CLIENT
          .newBuilder()
          .connectTimeout(5, TimeUnit.SECONDS)
          .readTimeout(10, TimeUnit.SECONDS)
          .build();

  private static final Pattern HREF_PATTERN = Pattern.compile("href=\"([^\"?]+)\"");
  private static final String FILE_MAP_CACHE_NAME = "winnative_file_map_v1.txt";
  private static final String FILE_MAP_CACHE_HEADER_PREFIX = "# timestamp=";
  private static final long FILE_MAP_CACHE_TTL_MS = 24L * 60L * 60L * 1000L;

  private static volatile boolean logEnabledCached = false;
  private static volatile boolean logEnabledResolved = false;

  /** Returns true only if the user has enabled download logging in Debug settings. */
  private static boolean logEnabled() {
    if (logEnabledResolved) return logEnabledCached;
    try {
      Context ctx = getAppContext();
      if (ctx == null) return false;
      logEnabledCached =
          PreferenceManager.getDefaultSharedPreferences(ctx)
              .getBoolean("enable_download_logs", false);
      logEnabledResolved = true;
      return logEnabledCached;
    } catch (Exception e) {
      return false;
    }
  }

  static void refreshLogEnabled() {
    logEnabledResolved = false;
  }

  private static Context getAppContext() {
    try {
      return PluviaApp.Companion.getInstance().getApplicationContext();
    } catch (Exception e) {
      return null;
    }
  }

  private static File getFileMapCacheFile() {
    Context context = getAppContext();
    return context != null ? new File(context.getFilesDir(), FILE_MAP_CACHE_NAME) : null;
  }

  // ---- Global file map (filename-lowercase → full download URL) ----
  private static final ConcurrentHashMap<String, String> fileMap = new ConcurrentHashMap<>();
  private static volatile boolean fileMapReady = false;
  private static final Object mapLock = new Object();

  // ----------------------------------------------------------------
  //  Public listener
  // ----------------------------------------------------------------
  public interface DownloadListener {
    void onProgress(long downloadedBytes, long totalBytes);
  }

  // ----------------------------------------------------------------
  //  WinNative-first download  (primary API)
  // ----------------------------------------------------------------

  /**
   * Downloads a file, trying WinNative.dev first for the same filename, falling back to the
   * original URL if WinNative.dev fails or does not host the file.
   *
   * @param originalUrl The original (typically GitHub) download URL.
   * @param file Destination file.
   * @param listener Optional progress callback.
   * @return true if the file was downloaded successfully from either source.
   */
  public static boolean downloadFileWinNativeFirst(
      String originalUrl, File file, DownloadListener listener) {
    String filename = extractFilename(originalUrl);
    if (filename != null) {
      ensureFileMap();
      String winUrl = fileMap.get(filename.toLowerCase(Locale.ROOT));
      if (winUrl != null) {
        if (logEnabled()) Log.d(TAG, "WinNative URL resolved: " + winUrl);
        if (downloadFile(winUrl, file, listener)) {
          if (logEnabled()) Log.d(TAG, "Download succeeded from WinNative.dev");
          return true;
        }
        if (logEnabled()) Log.w(TAG, "WinNative download failed, falling back to: " + originalUrl);
        file.delete();
      } else {
        if (logEnabled())
          Log.d(TAG, "File not found on WinNative.dev, using original: " + originalUrl);
      }
    }
    return downloadFile(originalUrl, file, listener);
  }

  /**
   * Legacy wrapper – delegates to {@link #downloadFileWinNativeFirst}. Kept for call-sites that
   * still pass a contentType (ignored now).
   */
  public static boolean downloadFileWithFallback(
      String contentType, String originalUrl, File file, DownloadListener listener) {
    return downloadFileWinNativeFirst(originalUrl, file, listener);
  }

  // ----------------------------------------------------------------
  //  File-map build / lookup
  // ----------------------------------------------------------------

  /**
   * Ensures the file map has been built at least once this session. Safe to call from any thread;
   * only the first caller actually crawls.
   */
  public static void ensureFileMap() {
    if (fileMapReady) return;
    synchronized (mapLock) {
      if (fileMapReady) return;
      refreshLogEnabled();
      if (!loadFileMapFromDisk()) {
        buildFileMap();
        persistFileMap();
      }
      fileMapReady = true;
    }
  }

  /**
   * Forces a fresh crawl of WinNative.dev/Downloads/ on next access. Call at the start of a wizard
   * session so newly-uploaded files are found.
   */
  public static void clearFileMap() {
    synchronized (mapLock) {
      fileMap.clear();
      fileMapReady = false;
      File cacheFile = getFileMapCacheFile();
      if (cacheFile != null && cacheFile.exists() && !cacheFile.delete() && logEnabled()) {
        Log.w(TAG, "Unable to delete WinNative file map cache: " + cacheFile.getAbsolutePath());
      }
    }
  }

  /**
   * @deprecated Use {@link #clearFileMap()} instead.
   */
  @Deprecated
  public static void clearDirectoryCache() {
    clearFileMap();
  }

  /** Returns the resolved WinNative URL for a given filename, or null if not hosted. */
  public static String resolveWinNativeUrl(String filename) {
    if (filename == null) return null;
    ensureFileMap();
    return fileMap.get(filename.toLowerCase(Locale.ROOT));
  }

  /** Variant that accepts (and ignores) a contentType for back-compat. */
  public static String resolveWinNativeUrl(String contentType, String filename) {
    return resolveWinNativeUrl(filename);
  }

  // ----------------------------------------------------------------
  //  Recursive directory crawler
  // ----------------------------------------------------------------

  /**
   * Recursively crawls {@link #WINNATIVE_ROOT} and populates {@link #fileMap} with every
   * downloadable file found (filename-lowercase → full URL).
   */
  private static void buildFileMap() {
    if (logEnabled()) Log.d(TAG, "Building WinNative file map from " + WINNATIVE_ROOT);
    long start = System.currentTimeMillis();
    fileMap.clear();
    crawlDirectory(WINNATIVE_ROOT, 0);
    if (logEnabled())
      Log.d(
          TAG,
          "WinNative file map built: "
              + fileMap.size()
              + " files in "
              + (System.currentTimeMillis() - start)
              + "ms");
  }

  private static boolean loadFileMapFromDisk() {
    File cacheFile = getFileMapCacheFile();
    if (cacheFile == null || !cacheFile.isFile()) {
      return false;
    }

    fileMap.clear();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8))) {
      String header = reader.readLine();
      if (header == null || !header.startsWith(FILE_MAP_CACHE_HEADER_PREFIX)) {
        return false;
      }

      long timestamp =
          Long.parseLong(header.substring(FILE_MAP_CACHE_HEADER_PREFIX.length()).trim());
      long ageMs = System.currentTimeMillis() - timestamp;
      if (ageMs > FILE_MAP_CACHE_TTL_MS) {
        if (!cacheFile.delete() && logEnabled()) {
          Log.w(TAG, "Unable to delete expired WinNative file map cache");
        }
        return false;
      }

      String line;
      while ((line = reader.readLine()) != null) {
        int separatorIndex = line.indexOf('\t');
        if (separatorIndex <= 0 || separatorIndex >= line.length() - 1) {
          continue;
        }
        fileMap.putIfAbsent(line.substring(0, separatorIndex), line.substring(separatorIndex + 1));
      }
    } catch (Exception e) {
      if (logEnabled()) Log.w(TAG, "Failed to load WinNative file map cache", e);
      fileMap.clear();
      return false;
    }

    if (fileMap.isEmpty()) {
      return false;
    }

    if (logEnabled()) {
      Log.d(TAG, "Loaded WinNative file map cache: " + fileMap.size() + " files");
    }
    return true;
  }

  private static void persistFileMap() {
    if (fileMap.isEmpty()) {
      return;
    }

    File cacheFile = getFileMapCacheFile();
    if (cacheFile == null) {
      return;
    }

    File tempFile = new File(cacheFile.getParentFile(), cacheFile.getName() + ".tmp");
    try (BufferedWriter writer =
        new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
      writer.write(FILE_MAP_CACHE_HEADER_PREFIX);
      writer.write(Long.toString(System.currentTimeMillis()));
      writer.newLine();

      for (Map.Entry<String, String> entry : fileMap.entrySet()) {
        String value = entry.getValue();
        if (value == null || value.isEmpty()) {
          continue;
        }
        writer.write(entry.getKey());
        writer.write('\t');
        writer.write(value);
        writer.newLine();
      }
    } catch (Exception e) {
      if (logEnabled()) Log.w(TAG, "Failed to persist WinNative file map cache", e);
      tempFile.delete();
      return;
    }

    if (cacheFile.exists() && !cacheFile.delete() && logEnabled()) {
      Log.w(TAG, "Unable to replace existing WinNative file map cache");
    }
    if (!tempFile.renameTo(cacheFile) && logEnabled()) {
      Log.w(TAG, "Unable to finalize WinNative file map cache write");
    }
  }

  /**
   * Fetches a single directory listing page, adds any file entries to the map, and recurses into
   * subdirectories up to {@link #MAX_CRAWL_DEPTH}.
   */
  private static void crawlDirectory(String dirUrl, int depth) {
    if (depth > MAX_CRAWL_DEPTH) return;

    String html;
    try {
      html = downloadString(dirUrl);
    } catch (Exception e) {
      if (logEnabled()) Log.w(TAG, "Crawl failed for " + dirUrl + ": " + e.getMessage());
      return;
    }
    if (html == null) return;

    // Parse href entries from Apache-style "Index of" listing
    Matcher matcher = HREF_PATTERN.matcher(html);
    List<String> subdirs = new ArrayList<>();

    while (matcher.find()) {
      String href = matcher.group(1);
      // Skip absolute paths, parent directory, and sorting query links
      if (href.startsWith("/")
          || href.startsWith("..")
          || href.startsWith("?")
          || href.startsWith("http")) {
        continue;
      }

      if (href.endsWith("/")) {
        // It's a subdirectory – queue for recursive crawl
        subdirs.add(dirUrl + href);
      } else {
        // It's a file – add to the map (lowercase key for case-insensitive lookup)
        String key = href.toLowerCase(Locale.ROOT);
        String fullUrl = dirUrl + href;
        // If duplicate filename across folders, keep the first one found
        // (closest to root = most likely the canonical location)
        fileMap.putIfAbsent(key, fullUrl);
      }
    }

    // Recurse into subdirectories
    for (String subdir : subdirs) {
      crawlDirectory(subdir, depth + 1);
    }
  }

  // ----------------------------------------------------------------
  //  Core download methods
  // ----------------------------------------------------------------

  /** Extracts the filename (last path segment) from a URL. */
  public static String extractFilename(String url) {
    if (url == null) return null;
    try {
      String path = new URL(url).getPath();
      int lastSlash = path.lastIndexOf('/');
      if (lastSlash >= 0 && lastSlash < path.length() - 1) {
        return path.substring(lastSlash + 1);
      }
    } catch (Exception ignored) {
      int queryStart = url.indexOf('?');
      String pathOnly = queryStart >= 0 ? url.substring(0, queryStart) : url;
      int lastSlash = pathOnly.lastIndexOf('/');
      if (lastSlash >= 0 && lastSlash < pathOnly.length() - 1) {
        return pathOnly.substring(lastSlash + 1);
      }
    }
    return null;
  }

  /**
   * Downloads a file from the given address with progress reporting. Uses OkHttp with HTTP/2 and
   * connection pooling for fast ramp-up.
   */
  public static boolean downloadFile(String address, File file, DownloadListener listener) {
    try {
      File parent = file.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        if (logEnabled())
          Log.w(TAG, "Unable to create download directory: " + parent.getAbsolutePath());
        return false;
      }

      Request request =
          new Request.Builder().url(address).header("Accept-Encoding", "identity").build();

      try (Response response = FILE_CLIENT.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          throw new IllegalStateException("HTTP " + response.code() + " for " + address);
        }

        ResponseBody body = response.body();
        if (body == null) {
          throw new IllegalStateException("Empty response body for " + address);
        }

        long lengthOfFile = body.contentLength();
        long total = 0;
        long lastUpdateTime = 0;

        if (listener != null) {
          listener.onProgress(0, lengthOfFile);
        }

        try (BufferedSource source = body.source();
            FileOutputStream fos = new FileOutputStream(file)) {
          Buffer buffer = new Buffer();
          long bytesRead;

          while ((bytesRead = source.read(buffer, READ_BUFFER_SIZE)) != -1) {
            buffer.writeTo(fos);
            total += bytesRead;
            if (listener != null) {
              long currentTime = System.currentTimeMillis();
              if (currentTime - lastUpdateTime > 80 || total == lengthOfFile) {
                listener.onProgress(total, lengthOfFile);
                lastUpdateTime = currentTime;
              }
            }
          }

          fos.flush();
        }

        if (listener != null) {
          listener.onProgress(total, total);
        }
        return true;
      }
    } catch (Exception e) {
      if (logEnabled()) Log.w(TAG, "Download failed for " + address, e);
      if (file.exists() && !file.delete() && logEnabled()) {
        Log.w(TAG, "Unable to delete partial download: " + file.getAbsolutePath());
      }
      return false;
    }
  }

  public static long fetchContentLength(String address) {
    if (address == null || address.isEmpty()) return -1L;
    Request request = new Request.Builder().url(address).head().build();
    try (Response response = STRING_CLIENT.newCall(request).execute()) {
      if (!response.isSuccessful()) return -1L;
      String header = response.header("Content-Length");
      if (header == null || header.isEmpty()) return -1L;
      try {
        return Long.parseLong(header);
      } catch (NumberFormatException nfe) {
        return -1L;
      }
    } catch (Exception e) {
      if (logEnabled()) Log.w(TAG, "HEAD failed for " + address, e);
      return -1L;
    }
  }

  /**
   * Downloads a URL as a String (used for JSON fetches and directory listings). Shares the
   * connection pool with file downloads so directory crawling warms up connections for subsequent
   * file downloads.
   */
  public static String downloadString(String address) {
    Request request = new Request.Builder().url(address).build();

    try (Response response = STRING_CLIENT.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IllegalStateException("HTTP " + response.code() + " for " + address);
      }
      ResponseBody body = response.body();
      return body != null ? body.string() : null;
    } catch (Exception e) {
      if (logEnabled()) Log.w(TAG, "String download failed for " + address, e);
      return null;
    }
  }
}
