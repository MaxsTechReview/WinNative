package com.winlator.cmod.feature.sync.google;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.tasks.Tasks;
import com.winlator.cmod.runtime.container.Container;
import com.winlator.cmod.shared.android.ActivityResultHost;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import timber.log.Timber;

public final class ContainerBackupManager {
  private static final String TAG = "ContainerBackup";
  private static final String DRIVE_ROOT_FOLDER_NAME = "WinNative";
  private static final String DRIVE_CONTAINERS_FOLDER_NAME = "Containers";
  private static final String DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file";
  private static final String PREFS_NAME = "google_store_login_sync";
  private static final String KEY_GOOGLE_SYNC_ENABLED = "google_sync_enabled";
  private static final int AUTH_SESSION_RETRY_COUNT = 5;
  private static final long AUTH_SESSION_RETRY_DELAY_MS = 750L;

  private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
  private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=UTF-8");
  private static final MediaType ZIP_MEDIA_TYPE = MediaType.get("application/zip");

  private static final OkHttpClient HTTP_CLIENT =
      new OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .writeTimeout(60, TimeUnit.SECONDS)
          .build();

  private ContainerBackupManager() {}

  public interface ResultCallback<T> {
    void onComplete(T result);
  }

  public static final class BackupResult {
    public final boolean success;
    public final String message;

    public BackupResult(boolean success, String message) {
      this.success = success;
      this.message = message;
    }
  }

  public static final class DriveBackupFile {
    public final String id;
    public final String name;

    public DriveBackupFile(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  public static final class RestorePreparation {
    public final boolean success;
    public final boolean needsSelection;
    public final String message;
    public final DriveBackupFile matchedFile;
    public final List<DriveBackupFile> candidates;

    public RestorePreparation(
        boolean success,
        boolean needsSelection,
        String message,
        DriveBackupFile matchedFile,
        List<DriveBackupFile> candidates) {
      this.success = success;
      this.needsSelection = needsSelection;
      this.message = message;
      this.matchedFile = matchedFile;
      this.candidates = candidates;
    }
  }

  public static void backupContainer(
      Activity activity, Container container, ResultCallback<BackupResult> callback) {
    EXECUTOR.execute(() -> post(callback, performBackup(activity, container)));
  }

  public static void prepareRestore(
      Activity activity, Container container, ResultCallback<RestorePreparation> callback) {
    EXECUTOR.execute(() -> post(callback, prepareRestoreInternal(activity, container)));
  }

  public static void restoreContainerFromDriveFile(
      Activity activity,
      Container container,
      DriveBackupFile driveFile,
      ResultCallback<BackupResult> callback) {
    EXECUTOR.execute(() -> post(callback, performRestore(activity, container, driveFile)));
  }

  private static <T> void post(ResultCallback<T> callback, T value) {
    MAIN_HANDLER.post(() -> callback.onComplete(value));
  }

  private static BackupResult performBackup(Activity activity, Container container) {
    try {
      Context context = activity.getApplicationContext();
      String accessToken = requireAccessToken(activity, context);
      if (accessToken == null) {
        return new BackupResult(
            false, "Google Drive authorization required. Please try again after granting access.");
      }

      File driveCRoot = new File(container.getRootDir(), ".wine/drive_c");
      File usersDir = new File(driveCRoot, "users");
      File programDataDir = new File(driveCRoot, "ProgramData");
      if (!usersDir.exists() && !programDataDir.exists()) {
        return new BackupResult(false, "No container data found to back up.");
      }

      byte[] zipBytes = zipContainerDirectories(usersDir, programDataDir);
      if (zipBytes.length == 0) {
        return new BackupResult(false, "Container backup is empty.");
      }

      String folderId = getOrCreateContainersFolder(accessToken);
      if (folderId == null) {
        return new BackupResult(false, "Failed to access WinNative/Containers on Google Drive.");
      }

      String fileName = buildBackupFileName(container);
      String existingFileId = findDriveFileId(accessToken, folderId, fileName);
      boolean uploaded =
          existingFileId != null
              ? updateDriveFile(accessToken, existingFileId, zipBytes)
              : createDriveFile(accessToken, folderId, fileName, zipBytes);

      return uploaded
          ? new BackupResult(true, "Container backup uploaded to Google Drive.")
          : new BackupResult(false, "Failed to upload container backup to Google Drive.");
    } catch (Exception error) {
      Timber.tag(TAG).e(error, "Container backup failed for %s", container.getName());
      return new BackupResult(false, "Container backup failed: " + error.getMessage());
    }
  }

  private static RestorePreparation prepareRestoreInternal(Activity activity, Container container) {
    try {
      Context context = activity.getApplicationContext();
      String accessToken = requireAccessToken(activity, context);
      if (accessToken == null) {
        return new RestorePreparation(
            false,
            false,
            "Google Drive authorization required. Please try again after granting access.",
            null,
            Collections.emptyList());
      }

      String folderId = getOrCreateContainersFolder(accessToken);
      if (folderId == null) {
        return new RestorePreparation(
            false,
            false,
            "Failed to access WinNative/Containers on Google Drive.",
            null,
            Collections.emptyList());
      }

      String expectedFileName = buildBackupFileName(container);
      DriveBackupFile matchedFile = findDriveFile(accessToken, folderId, expectedFileName);
      if (matchedFile != null) {
        return new RestorePreparation(true, false, null, matchedFile, Collections.emptyList());
      }

      List<DriveBackupFile> candidates = listDriveFiles(accessToken, folderId);
      if (candidates.isEmpty()) {
        return new RestorePreparation(
            false,
            false,
            "No container backups were found in Google Drive.",
            null,
            Collections.emptyList());
      }

      return new RestorePreparation(
          true,
          true,
          "No exact backup matched this container. Select a backup to restore.",
          null,
          candidates);
    } catch (Exception error) {
      Timber.tag(TAG).e(error, "Container restore preparation failed for %s", container.getName());
      return new RestorePreparation(
          false,
          false,
          "Container restore failed: " + error.getMessage(),
          null,
          Collections.emptyList());
    }
  }

  private static BackupResult performRestore(
      Activity activity, Container container, DriveBackupFile driveFile) {
    try {
      Context context = activity.getApplicationContext();
      String accessToken = requireAccessToken(activity, context);
      if (accessToken == null) {
        return new BackupResult(
            false, "Google Drive authorization required. Please try again after granting access.");
      }

      byte[] zipBytes = downloadDriveFile(accessToken, driveFile.id);
      if (zipBytes == null || zipBytes.length == 0) {
        return new BackupResult(false, "Downloaded container backup is empty.");
      }

      File driveCRoot = new File(container.getRootDir(), ".wine/drive_c");
      File usersDir = new File(driveCRoot, "users");
      File programDataDir = new File(driveCRoot, "ProgramData");

      deleteRecursively(usersDir);
      deleteRecursively(programDataDir);
      if (!driveCRoot.exists() && !driveCRoot.mkdirs()) {
        return new BackupResult(false, "Failed to prepare the container for restore.");
      }

      unzipToDirectory(zipBytes, driveCRoot);
      return new BackupResult(true, "Container backup restored successfully.");
    } catch (Exception error) {
      Timber.tag(TAG)
          .e(error, "Container restore failed for %s from %s", container.getName(), driveFile.name);
      return new BackupResult(false, "Container restore failed: " + error.getMessage());
    }
  }

  private static String requireAccessToken(Activity activity, Context context) throws Exception {
    if (!isGoogleSyncEnabled(context)) {
      return null;
    }
    if (!awaitAuthenticatedSession(activity)) {
      throw new IllegalStateException("Not signed in to Google Play Games. Please sign in first.");
    }
    return getDriveAccessToken(activity);
  }

  private static boolean isGoogleSyncEnabled(Context context) {
    return context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_GOOGLE_SYNC_ENABLED, false);
  }

  private static boolean awaitAuthenticatedSession(Activity activity) throws InterruptedException {
    for (int attempt = 0; attempt < AUTH_SESSION_RETRY_COUNT; attempt++) {
      if (isAuthenticatedBlocking(activity)) {
        return true;
      }
      if (attempt < AUTH_SESSION_RETRY_COUNT - 1) {
        Thread.sleep(AUTH_SESSION_RETRY_DELAY_MS);
      }
    }
    return false;
  }

  private static boolean isAuthenticatedBlocking(Activity activity) {
    try {
      Object result =
          Tasks.await(
              PlayGames.getGamesSignInClient(activity).isAuthenticated(), 10, TimeUnit.SECONDS);
      return result != null
          && Boolean.TRUE.equals(result.getClass().getMethod("isAuthenticated").invoke(result));
    } catch (TimeoutException timeout) {
      Timber.tag(TAG).e(timeout, "Timed out waiting for Google authentication state");
      return false;
    } catch (Exception error) {
      Timber.tag(TAG).e(error, "Failed to read Google authentication state");
      return false;
    }
  }

  private static String getDriveAccessToken(Activity activity) {
    try {
      AuthorizationRequest authRequest =
          AuthorizationRequest.builder()
              .setRequestedScopes(Collections.singletonList(new Scope(DRIVE_FILE_SCOPE)))
              .build();

      AuthorizationResult authResult =
          Tasks.await(Identity.getAuthorizationClient(activity).authorize(authRequest));
      if (authResult == null) {
        return null;
      }

      if (authResult.hasResolution()) {
        if (authResult.getPendingIntent() != null) {
          activity.runOnUiThread(
              () -> {
                try {
                  if (activity instanceof ActivityResultHost) {
                    ((ActivityResultHost) activity)
                        .launchDriveAuthRequest(authResult.getPendingIntent().getIntentSender());
                  } else {
                    Timber.tag(TAG)
                        .e(
                            "Activity %s cannot launch Drive auth flow",
                            activity.getClass().getSimpleName());
                  }
                } catch (Exception error) {
                  Timber.tag(TAG).e(error, "Failed to launch Drive consent UI");
                }
              });
        }
        return null;
      }

      return authResult.getAccessToken();
    } catch (Exception error) {
      Timber.tag(TAG).e(error, "Failed to get Drive access token");
      return null;
    }
  }

  private static byte[] zipContainerDirectories(File usersDir, File programDataDir)
      throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
      if (usersDir.exists()) {
        addDirectoryToZip(zipOutputStream, usersDir, "users");
      }
      if (programDataDir.exists()) {
        addDirectoryToZip(zipOutputStream, programDataDir, "ProgramData");
      }
    }
    return outputStream.toByteArray();
  }

  private static void addDirectoryToZip(
      ZipOutputStream zipOutputStream, File directory, String rootEntry) throws Exception {
    String dirEntry = rootEntry.endsWith("/") ? rootEntry : rootEntry + "/";
    zipOutputStream.putNextEntry(new ZipEntry(dirEntry));
    zipOutputStream.closeEntry();

    File[] children = directory.listFiles();
    if (children == null) {
      return;
    }
    for (File child : children) {
      addFileToZip(zipOutputStream, child, rootEntry + "/" + child.getName());
    }
  }

  private static void addFileToZip(ZipOutputStream zipOutputStream, File file, String entryName)
      throws Exception {
    if (file.isDirectory()) {
      zipOutputStream.putNextEntry(new ZipEntry(entryName + "/"));
      zipOutputStream.closeEntry();
      File[] children = file.listFiles();
      if (children == null) {
        return;
      }
      for (File child : children) {
        addFileToZip(zipOutputStream, child, entryName + "/" + child.getName());
      }
      return;
    }

    zipOutputStream.putNextEntry(new ZipEntry(entryName));
    try (FileInputStream inputStream = new FileInputStream(file)) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = inputStream.read(buffer)) > 0) {
        zipOutputStream.write(buffer, 0, count);
      }
    }
    zipOutputStream.closeEntry();
  }

  private static void unzipToDirectory(byte[] zipBytes, File destinationDirectory)
      throws Exception {
    String destinationPath = destinationDirectory.getCanonicalPath() + File.separator;
    try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        File outputFile = new File(destinationDirectory, entry.getName());
        String outputPath = outputFile.getCanonicalPath();
        if (!outputPath.equals(destinationDirectory.getCanonicalPath())
            && !outputPath.startsWith(destinationPath)) {
          throw new SecurityException("Zip entry escapes the target directory");
        }

        if (entry.isDirectory()) {
          outputFile.mkdirs();
        } else {
          File parent = outputFile.getParentFile();
          if (parent != null && !parent.exists()) {
            parent.mkdirs();
          }
          try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = zipInputStream.read(buffer)) > 0) {
              outputStream.write(buffer, 0, count);
            }
          }
        }
        zipInputStream.closeEntry();
      }
    }
  }

  private static void deleteRecursively(File file) {
    if (file == null || !file.exists()) {
      return;
    }
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          deleteRecursively(child);
        }
      }
    }
    if (!file.delete()) {
      Timber.tag(TAG).w("Failed to delete %s during restore cleanup", file.getAbsolutePath());
    }
  }

  private static String buildBackupFileName(Container container) {
    String containerName = container.getName();
    if (containerName == null || containerName.trim().isEmpty()) {
      containerName = "Container-" + container.id;
    }
    String safeName =
        containerName.replace('/', '_').replace('\\', '_').replace('\n', '_').replace('\r', '_');
    return safeName + ".zip";
  }

  private static String getOrCreateContainersFolder(String accessToken) throws Exception {
    String rootFolderId = getOrCreateFolder(accessToken, null, DRIVE_ROOT_FOLDER_NAME);
    if (rootFolderId == null) {
      return null;
    }
    return getOrCreateFolder(accessToken, rootFolderId, DRIVE_CONTAINERS_FOLDER_NAME);
  }

  private static String getOrCreateFolder(String accessToken, String parentId, String folderName)
      throws Exception {
    DriveBackupFile folder = findDriveFile(accessToken, parentId, folderName, true);
    if (folder != null) {
      return folder.id;
    }

    JSONObject metadata = new JSONObject();
    metadata.put("name", folderName);
    metadata.put("mimeType", "application/vnd.google-apps.folder");
    if (parentId != null) {
      metadata.put("parents", new JSONArray().put(parentId));
    }

    Request request =
        new Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .addHeader("Authorization", "Bearer " + accessToken)
            .post(RequestBody.create(metadata.toString(), JSON_MEDIA_TYPE))
            .build();

    try (okhttp3.Response response = HTTP_CLIENT.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        Timber.tag(TAG)
            .e(
                "Failed to create folder %s: %d %s",
                folderName, response.code(), response.message());
        return null;
      }
      JSONObject json = new JSONObject(response.body() != null ? response.body().string() : "{}");
      return json.optString("id", null);
    }
  }

  private static String findDriveFileId(String accessToken, String folderId, String fileName)
      throws Exception {
    DriveBackupFile file = findDriveFile(accessToken, folderId, fileName);
    return file != null ? file.id : null;
  }

  private static DriveBackupFile findDriveFile(String accessToken, String folderId, String fileName)
      throws Exception {
    return findDriveFile(accessToken, folderId, fileName, false);
  }

  private static DriveBackupFile findDriveFile(
      String accessToken, String folderId, String fileName, boolean folderOnly) throws Exception {
    StringBuilder queryBuilder = new StringBuilder();
    queryBuilder.append("name='").append(escapeDriveQuery(fileName)).append("' and trashed=false");
    if (folderId != null) {
      queryBuilder.append(" and '").append(folderId).append("' in parents");
    }
    if (folderOnly) {
      queryBuilder.append(" and mimeType='application/vnd.google-apps.folder'");
    }

    String url =
        "https://www.googleapis.com/drive/v3/files?q="
            + URLEncoder.encode(queryBuilder.toString(), "UTF-8")
            + "&fields=files(id,name)&pageSize=1";
    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .get()
            .build();

    try (okhttp3.Response response = HTTP_CLIENT.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        return null;
      }
      JSONObject json = new JSONObject(response.body() != null ? response.body().string() : "{}");
      JSONArray files = json.optJSONArray("files");
      if (files == null || files.length() == 0) {
        return null;
      }
      JSONObject file = files.getJSONObject(0);
      return new DriveBackupFile(file.getString("id"), file.getString("name"));
    }
  }

  private static List<DriveBackupFile> listDriveFiles(String accessToken, String folderId)
      throws Exception {
    String query =
        "'"
            + folderId
            + "' in parents and trashed=false and mimeType!='application/vnd.google-apps.folder'";
    String url =
        "https://www.googleapis.com/drive/v3/files?q="
            + URLEncoder.encode(query, "UTF-8")
            + "&fields=files(id,name)&pageSize=200";
    Request request =
        new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .get()
            .build();

    ArrayList<DriveBackupFile> files = new ArrayList<>();
    try (okhttp3.Response response = HTTP_CLIENT.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        return files;
      }
      JSONObject json = new JSONObject(response.body() != null ? response.body().string() : "{}");
      JSONArray results = json.optJSONArray("files");
      if (results == null) {
        return files;
      }
      for (int i = 0; i < results.length(); i++) {
        JSONObject file = results.getJSONObject(i);
        files.add(new DriveBackupFile(file.getString("id"), file.getString("name")));
      }
    }
    files.sort(Comparator.comparing(file -> file.name.toLowerCase()));
    return files;
  }

  private static boolean createDriveFile(
      String accessToken, String folderId, String fileName, byte[] data) throws Exception {
    JSONObject metadata = new JSONObject();
    metadata.put("name", fileName);
    metadata.put("parents", new JSONArray().put(folderId));

    String boundary = "winnative_container_boundary_" + System.currentTimeMillis();
    byte[] body = buildMultipartRelatedBody(boundary, metadata.toString(), data);

    Request request =
        new Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .addHeader("Authorization", "Bearer " + accessToken)
            .post(
                RequestBody.create(body, MediaType.get("multipart/related; boundary=" + boundary)))
            .build();

    try (okhttp3.Response response = HTTP_CLIENT.newCall(request).execute()) {
      if (response.isSuccessful()) {
        return true;
      }
      Timber.tag(TAG)
          .e(
              "Failed to create Drive file %s: %d %s",
              fileName, response.code(), response.message());
      return false;
    }
  }

  private static boolean updateDriveFile(String accessToken, String fileId, byte[] data)
      throws Exception {
    Request request =
        new Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/" + fileId + "?uploadType=media")
            .addHeader("Authorization", "Bearer " + accessToken)
            .patch(RequestBody.create(data, ZIP_MEDIA_TYPE))
            .build();

    try (okhttp3.Response response = HTTP_CLIENT.newCall(request).execute()) {
      if (response.isSuccessful()) {
        return true;
      }
      Timber.tag(TAG)
          .e("Failed to update Drive file %s: %d %s", fileId, response.code(), response.message());
      return false;
    }
  }

  private static byte[] downloadDriveFile(String accessToken, String fileId) throws Exception {
    Request request =
        new Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media")
            .addHeader("Authorization", "Bearer " + accessToken)
            .get()
            .build();

    try (okhttp3.Response response = HTTP_CLIENT.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        return null;
      }
      return response.body().bytes();
    }
  }

  private static byte[] buildMultipartRelatedBody(
      String boundary, String jsonMetadata, byte[] fileData) throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    String crlf = "\r\n";

    outputStream.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
    outputStream.write(
        ("Content-Type: application/json; charset=UTF-8" + crlf + crlf)
            .getBytes(StandardCharsets.UTF_8));
    outputStream.write(jsonMetadata.getBytes(StandardCharsets.UTF_8));
    outputStream.write(crlf.getBytes(StandardCharsets.UTF_8));
    outputStream.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
    outputStream.write(
        ("Content-Type: application/zip" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
    outputStream.write(fileData);
    outputStream.write(crlf.getBytes(StandardCharsets.UTF_8));
    outputStream.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));

    return outputStream.toByteArray();
  }

  private static String escapeDriveQuery(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }
}
