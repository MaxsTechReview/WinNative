/**
 * Shared download helper with WinNative.dev-first resolution.
 *
 * On first use within a session the class recursively crawls
 * https://WinNative.dev/Downloads/ and every subdirectory it finds,
 * building a complete  filename → full-URL  map.  When any download
 * is requested the filename from the original (GitHub) URL is looked
 * up in that map.  If the file exists on WinNative.dev the download
 * is attempted from there first; only if it fails does it fall back
 * to the original URL.
 *
 * The map is rebuilt automatically when clearFileMap() is called
 * (e.g. at the start of a new wizard session).
 */
package com.winlator.cmod.contents;

import android.util.Log;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.PluviaApp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Downloader {

    private static final String TAG = "Downloader";
    private static final String WINNATIVE_ROOT = "https://WinNative.dev/Downloads/";
    private static final int MAX_CRAWL_DEPTH = 10;
    private static final int FILE_CONNECT_TIMEOUT_MS = 15000;
    private static final int FILE_READ_TIMEOUT_MS = 30000;
    private static final int STRING_CONNECT_TIMEOUT_MS = 5000;
    private static final int STRING_READ_TIMEOUT_MS = 10000;
    private static final Pattern HREF_PATTERN = Pattern.compile("href=\"([^\"?]+)\"");

    /** Returns true only if the user has enabled download logging in Debug settings. */
    private static boolean logEnabled() {
        try {
            android.content.Context ctx = PluviaApp.Companion.getInstance().getApplicationContext();
            if (ctx == null) return false;
            return PreferenceManager.getDefaultSharedPreferences(ctx)
                    .getBoolean("enable_download_logs", false);
        } catch (Exception e) {
            return false;
        }
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
     * Downloads a file, trying WinNative.dev first for the same filename,
     * falling back to the original URL if WinNative.dev fails or does not
     * host the file.
     *
     * @param originalUrl  The original (typically GitHub) download URL.
     * @param file         Destination file.
     * @param listener     Optional progress callback.
     * @return true if the file was downloaded successfully from either source.
     */
    public static boolean downloadFileWinNativeFirst(String originalUrl, File file, DownloadListener listener) {
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
                if (logEnabled()) Log.d(TAG, "File not found on WinNative.dev, using original: " + originalUrl);
            }
        }
        return downloadFile(originalUrl, file, listener);
    }

    /**
     * Legacy wrapper – delegates to {@link #downloadFileWinNativeFirst}.
     * Kept for call-sites that still pass a contentType (ignored now).
     */
    public static boolean downloadFileWithFallback(String contentType, String originalUrl, File file, DownloadListener listener) {
        return downloadFileWinNativeFirst(originalUrl, file, listener);
    }

    // ----------------------------------------------------------------
    //  File-map build / lookup
    // ----------------------------------------------------------------

    /**
     * Ensures the file map has been built at least once this session.
     * Safe to call from any thread; only the first caller actually crawls.
     */
    public static void ensureFileMap() {
        if (fileMapReady) return;
        synchronized (mapLock) {
            if (fileMapReady) return;
            buildFileMap();
            fileMapReady = true;
        }
    }

    /**
     * Forces a fresh crawl of WinNative.dev/Downloads/ on next access.
     * Call at the start of a wizard session so newly-uploaded files are found.
     */
    public static void clearFileMap() {
        synchronized (mapLock) {
            fileMap.clear();
            fileMapReady = false;
        }
    }

    /** @deprecated Use {@link #clearFileMap()} instead. */
    @Deprecated
    public static void clearDirectoryCache() {
        clearFileMap();
    }

    /**
     * Returns the resolved WinNative URL for a given filename, or null if not hosted.
     */
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
     * Recursively crawls {@link #WINNATIVE_ROOT} and populates {@link #fileMap}
     * with every downloadable file found (filename-lowercase → full URL).
     */
    private static void buildFileMap() {
        if (logEnabled()) Log.d(TAG, "Building WinNative file map from " + WINNATIVE_ROOT);
        long start = System.currentTimeMillis();
        fileMap.clear();
        crawlDirectory(WINNATIVE_ROOT, 0);
        if (logEnabled()) Log.d(TAG, "WinNative file map built: " + fileMap.size() + " files in " +
                (System.currentTimeMillis() - start) + "ms");
    }

    /**
     * Fetches a single directory listing page, adds any file entries to the map,
     * and recurses into subdirectories up to {@link #MAX_CRAWL_DEPTH}.
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
            if (href.startsWith("/") || href.startsWith("..") || href.startsWith("?") || href.startsWith("http")) {
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

    /**
     * Extracts the filename (last path segment) from a URL.
     */
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
     * Downloads a file from the given address with progress reporting.
     */
    public static boolean downloadFile(String address, File file, DownloadListener listener) {
        HttpURLConnection connection = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                if (logEnabled()) Log.w(TAG, "Unable to create download directory: " + parent.getAbsolutePath());
                return false;
            }

            connection = openConnection(address, FILE_CONNECT_TIMEOUT_MS, FILE_READ_TIMEOUT_MS, true);
            long lengthOfFile = connection.getContentLengthLong();
            long total = 0;
            long lastUpdateTime = 0;

            if (listener != null) {
                listener.onProgress(0, lengthOfFile);
            }

            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(file)) {
                byte[] data = new byte[8192];
                int count;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);
                    if (listener != null) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUpdateTime > 80 || total == lengthOfFile) {
                            listener.onProgress(total, lengthOfFile);
                            lastUpdateTime = currentTime;
                        }
                    }
                }

                output.flush();
            }

            // Always report 100% on successful completion — Content-Length
            // can differ from actual bytes received (CDN quirks, chunked
            // encoding, etc.), so use total/total to guarantee 100%.
            if (listener != null) {
                listener.onProgress(total, total);
            }
            return true;
        } catch (Exception e) {
            if (logEnabled()) Log.w(TAG, "Download failed for " + address, e);
            if (file.exists() && !file.delete() && logEnabled()) {
                Log.w(TAG, "Unable to delete partial download: " + file.getAbsolutePath());
            }
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Downloads a URL as a String (used for JSON fetches and directory listings).
     */
    public static String downloadString(String address) {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(address, STRING_CONNECT_TIMEOUT_MS, STRING_READ_TIMEOUT_MS, false);
            StringBuilder sb = new StringBuilder();
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            if (logEnabled()) Log.w(TAG, "String download failed for " + address, e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection openConnection(
            String address,
            int connectTimeoutMs,
            int readTimeoutMs,
            boolean identityEncoding
    ) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        if (identityEncoding) {
            connection.setRequestProperty("Accept-Encoding", "identity");
        }
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
            throw new IllegalStateException("HTTP " + responseCode + " for " + address);
        }
        return connection;
    }
}
