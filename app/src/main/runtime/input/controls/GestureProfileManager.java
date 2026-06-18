package com.winlator.cmod.runtime.input.controls;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.app.config.SettingsConfig;
import com.winlator.cmod.runtime.input.ui.TouchGestureConfig;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

public class GestureProfileManager {
  private static final int ASSET_PROFILE_SYNC_REVISION = 1;
  public static final int DEFAULT_PROFILE_ID = 1;
  public static final String DEFAULT_PROFILE_NAME = "Default";

  private final Context context;
  private ArrayList<GestureProfile> profiles;
  private int maxProfileId;
  private boolean profilesLoaded = false;

  public GestureProfileManager(Context context) {
    this.context = context;
  }

  public static File getProfilesDir(Context context) {
    File dir = new File(context.getFilesDir(), "gesture_profiles");
    if (!dir.isDirectory()) dir.mkdir();
    return dir;
  }

  public static File getBackupsDir(Context context) {
    File dir = new File(context.getFilesDir(), "gesture_profile_backups");
    if (!dir.isDirectory()) dir.mkdir();
    return dir;
  }

  public static File getBackupFile(Context context, int id) {
    return new File(getBackupsDir(context), "gesture-" + id + ".gcp");
  }

  public static void backupProfile(Context context, int id) {
    File working = GestureProfile.getProfileFile(context, id);
    if (working.isFile()) FileUtils.copy(working, getBackupFile(context, id));
  }

  public ArrayList<GestureProfile> getProfiles() {
    if (!profilesLoaded) loadProfiles();
    return profiles;
  }

  public List<String> getProfileNames() {
    List<String> names = new ArrayList<>();
    for (GestureProfile profile : getProfiles()) names.add(profile.getName());
    return names;
  }

  public int indexOfProfile(int id) {
    ArrayList<GestureProfile> list = getProfiles();
    for (int i = 0; i < list.size(); i++) if (list.get(i).id == id) return i;
    return -1;
  }

  private void copyAssetProfilesIfNeeded() {
    getProfilesDir(context);

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    int newVersion = AppUtils.getVersionCode(context);
    int oldVersion = preferences.getInt("gestures_app_version", 0);
    int oldSyncRevision = preferences.getInt("gestures_asset_sync_revision", 0);
    if (oldVersion == newVersion && oldSyncRevision >= ASSET_PROFILE_SYNC_REVISION) return;
    preferences
        .edit()
        .putInt("gestures_app_version", newVersion)
        .putInt("gestures_asset_sync_revision", ASSET_PROFILE_SYNC_REVISION)
        .apply();

    try {
      AssetManager assetManager = context.getAssets();
      String[] assetFiles = assetManager.list("gestures/profiles");
      if (assetFiles == null) return;
      for (String assetFile : assetFiles) {
        String assetPath = "gestures/profiles/" + assetFile;
        String json = FileUtils.readString(context, assetPath);
        GestureProfile originProfile = loadProfileFromJson(context, json);
        if (originProfile == null) continue;
        File workingFile = GestureProfile.getProfileFile(context, originProfile.id);
        if (!workingFile.isFile()) FileUtils.writeString(workingFile, json);
        FileUtils.writeString(getBackupFile(context, originProfile.id), json);
      }
    } catch (IOException e) {
    }
  }

  public void loadProfiles() {
    File profilesDir = getProfilesDir(context);
    copyAssetProfilesIfNeeded();

    ArrayList<GestureProfile> loaded = new ArrayList<>();
    File[] files = profilesDir.listFiles();
    if (files != null) {
      for (File file : files) {
        GestureProfile profile = loadProfile(context, file);
        if (profile != null) {
          loaded.add(profile);
          maxProfileId = Math.max(maxProfileId, profile.id);
        }
      }
    }

    Collections.sort(loaded);
    this.profiles = loaded;
    maxProfileId = Math.max(maxProfileId, DEFAULT_PROFILE_ID);
    profilesLoaded = true;
  }

  public GestureProfile createProfile(String name) {
    if (!profilesLoaded) loadProfiles();
    int newId = ++maxProfileId;
    GestureProfile profile = new GestureProfile(context, newId);
    profile.setName(name);
    profile.setConfigJson(TouchGestureConfig.blankJson());
    profile.save();
    backupProfile(context, newId);
    profiles.add(profile);
    return profile;
  }

  public GestureProfile duplicateProfile(GestureProfile source) {
    if (!profilesLoaded) loadProfiles();
    String newName;
    for (int i = 1; ; i++) {
      newName = source.getName() + " (" + i + ")";
      boolean found = false;
      for (GestureProfile profile : profiles) {
        if (profile.getName().equals(newName)) {
          found = true;
          break;
        }
      }
      if (!found) break;
    }

    int newId = ++maxProfileId;
    GestureProfile profile = new GestureProfile(context, newId);
    profile.setName(newName);
    profile.setConfigJson(source.getConfigJson());
    profile.save();
    backupProfile(context, newId);
    profiles.add(profile);
    return profile;
  }

  public void renameProfile(GestureProfile profile, String newName) {
    if (profile == null || newName == null || newName.trim().isEmpty()) return;
    profile.setName(newName.trim());
    profile.save();
    backupProfile(context, profile.id);
  }

  public void removeProfile(GestureProfile profile) {
    if (profile == null) return;
    File file = GestureProfile.getProfileFile(context, profile.id);
    if (file.isFile()) file.delete();
    if (profiles != null) profiles.remove(profile);
    File backup = getBackupFile(context, profile.id);
    if (backup.isFile()) backup.delete();
  }

  // Live-save while editing; materializes a transient Default into a real file when first edited.
  public void saveProfile(GestureProfile profile) {
    if (profile == null) return;
    if (!profilesLoaded) loadProfiles();
    profile.save();
    if (getProfile(profile.id) == null) {
      profiles.add(profile);
      maxProfileId = Math.max(maxProfileId, profile.id);
      backupProfile(context, profile.id);
      Collections.sort(profiles);
    }
  }

  public synchronized GestureProfile importProfile(JSONObject data) {
    try {
      if (!profilesLoaded || profiles == null) loadProfiles();
      String profileName = data.optString("name", "").trim();
      if (profileName.isEmpty()) {
        Log.e("GestureProfileManager", "importProfile: data missing 'name'");
        return null;
      }

      GestureProfile existingProfile = findProfileByName(profileName);
      int targetId = existingProfile != null ? existingProfile.id : ++maxProfileId;
      File targetFile = GestureProfile.getProfileFile(context, targetId);
      data.put("id", targetId);
      FileUtils.writeString(targetFile, data.toString());
      backupProfile(context, targetId);
      GestureProfile newProfile = loadProfile(context, targetFile);
      if (newProfile == null) {
        newProfile = new GestureProfile(context, targetId);
        newProfile.setName(profileName);
      }

      int foundIndex = existingProfile != null ? profiles.indexOf(existingProfile) : -1;
      if (foundIndex != -1) profiles.set(foundIndex, newProfile);
      else profiles.add(newProfile);
      Collections.sort(profiles);
      return newProfile;
    } catch (JSONException e) {
      Log.e("GestureProfileManager", "importProfile: JSONException", e);
      return null;
    }
  }

  private GestureProfile findProfileByName(String name) {
    String normalizedName = normalizeProfileName(name);
    for (GestureProfile profile : profiles) {
      if (normalizeProfileName(profile.getName()).equals(normalizedName)) return profile;
    }
    return null;
  }

  private String normalizeProfileName(String name) {
    if (name == null) return "";
    String trimmed = name.trim();
    if (trimmed.toLowerCase(Locale.ROOT).endsWith(".gcp")) {
      trimmed = trimmed.substring(0, trimmed.length() - 4);
    }
    return trimmed.trim().toLowerCase(Locale.ROOT);
  }

  public File exportProfile(GestureProfile profile) {
    File destination;
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
    String winlatorPath = sp.getString("winlator_path_uri", null);
    if (winlatorPath != null) {
      Uri winlatorUri = Uri.parse(winlatorPath);
      destination =
          new File(
              FileUtils.getFilePathFromUri(context, winlatorUri),
              "Gestures/" + profile.getName() + ".gcp");
    } else {
      destination =
          new File(SettingsConfig.DEFAULT_WINLATOR_PATH, "Gestures/" + profile.getName() + ".gcp");
    }
    File parent = destination.getParentFile();
    if (parent != null && !parent.isDirectory()) parent.mkdirs();
    File working = GestureProfile.getProfileFile(context, profile.id);
    if (!working.isFile()) profile.save();
    FileUtils.copy(working, destination);
    MediaScannerConnection.scanFile(
        context, new String[] {destination.getAbsolutePath()}, null, null);
    return destination.isFile() ? destination : null;
  }

  public GestureProfile getProfile(int id) {
    for (GestureProfile profile : getProfiles()) if (profile.id == id) return profile;
    return null;
  }

  // Resolves the profile a game should use; falls back to a transient Default (stock config) when
  // none exist, which materializes into a real profile the moment it is edited and saved.
  public GestureProfile getDefaultProfile() {
    if (!profilesLoaded) loadProfiles();
    GestureProfile def = getProfile(DEFAULT_PROFILE_ID);
    if (def != null) return def;
    GestureProfile transientDefault = new GestureProfile(context, DEFAULT_PROFILE_ID);
    transientDefault.setName(DEFAULT_PROFILE_NAME);
    return transientDefault;
  }

  public static GestureProfile loadProfile(Context context, File file) {
    return loadProfileFromJson(context, FileUtils.readString(file));
  }

  private static GestureProfile loadProfileFromJson(Context context, String json) {
    if (json == null || json.trim().isEmpty()) return null;
    try {
      JSONObject o = new JSONObject(json);
      if (!o.has("id") || !o.has("name")) return null;
      GestureProfile profile = new GestureProfile(context, o.getInt("id"));
      profile.setName(o.getString("name"));
      JSONObject cfg = o.optJSONObject("config");
      profile.setConfigJson(cfg != null ? cfg.toString() : "{}");
      return profile;
    } catch (JSONException e) {
      return null;
    }
  }

}
