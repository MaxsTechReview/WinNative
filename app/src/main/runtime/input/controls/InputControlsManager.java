package com.winlator.cmod.runtime.input.controls;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.JsonReader;
import android.util.Log;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.app.config.SettingsConfig;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

public class InputControlsManager {
  private static final int ASSET_PROFILE_SYNC_REVISION = 3;
  /** Profile IDs at or below this value were shipped as read-only built-ins (assets/inputcontrols/profiles/controls-*.icp).
   * Edits to one of these always go through a duplicate first so the original layout stays pristine. */
  public static final int LAST_BUILTIN_PROFILE_ID = 6;
  /** Asset profile ID that holds the canonical "Virtual Gamepad" layout (controls-3.icp).
   * Used as the post-migration target when legacy Xbox/PS profiles are converted to label themes. */
  public static final int VIRTUAL_GAMEPAD_BUILTIN_ID = 3;
  /** Legacy Playstation Controller asset profile (controls-4.icp) — superseded by LabelTheme.PLAYSTATION. */
  public static final int LEGACY_PS_PROFILE_ID = 4;
  /** Legacy Xbox Controller asset profile (controls-5.icp) — superseded by LabelTheme.XBOX. */
  public static final int LEGACY_XBOX_PROFILE_ID = 5;

  private final Context context;
  private ArrayList<ControlsProfile> profiles;
  private int maxProfileId;
  private boolean profilesLoaded = false;

  public static boolean isBuiltinProfile(ControlsProfile profile) {
    return profile != null && profile.id <= LAST_BUILTIN_PROFILE_ID;
  }

  /** Returns true for asset profiles whose role is now covered by a LabelTheme — these should be
   * hidden from the user-facing profile picker so the new flow stays uncluttered. */
  public static boolean isLegacyLabelOnlyProfile(ControlsProfile profile) {
    if (profile == null) return false;
    return profile.id == LEGACY_PS_PROFILE_ID || profile.id == LEGACY_XBOX_PROFILE_ID;
  }

  public InputControlsManager(Context context) {
    this.context = context;
  }

  public static File getProfilesDir(Context context) {
    File profilesDir = new File(context.getFilesDir(), "profiles");
    if (!profilesDir.isDirectory()) profilesDir.mkdir();
    return profilesDir;
  }

  public ArrayList<ControlsProfile> getProfiles() {
    return getProfiles(false);
  }

  public ArrayList<ControlsProfile> getProfiles(boolean ignoreTemplates) {
    if (!profilesLoaded) loadProfiles(false);
    if (ignoreTemplates) {
      ArrayList<ControlsProfile> filteredProfiles = new ArrayList<>();
      for (ControlsProfile profile : profiles)
        if (!profile.isTemplate()) filteredProfiles.add(profile);
      return filteredProfiles;
    }
    return profiles;
  }

  private void copyAssetProfilesIfNeeded() {
    File profilesDir = InputControlsManager.getProfilesDir(context);
    if (FileUtils.isEmpty(profilesDir)) {
      FileUtils.copy(context, "inputcontrols/profiles", profilesDir);
      return;
    }

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

    int newVersion = AppUtils.getVersionCode(context);
    int oldVersion = preferences.getInt("inputcontrols_app_version", 0);
    int oldSyncRevision = preferences.getInt("inputcontrols_asset_sync_revision", 0);
    if (oldVersion == newVersion && oldSyncRevision >= ASSET_PROFILE_SYNC_REVISION) return;
    preferences
        .edit()
        .putInt("inputcontrols_app_version", newVersion)
        .putInt("inputcontrols_asset_sync_revision", ASSET_PROFILE_SYNC_REVISION)
        .apply();

    File[] files = profilesDir.listFiles();
    if (files == null) return;

    try {
      AssetManager assetManager = context.getAssets();
      String[] assetFiles = assetManager.list("inputcontrols/profiles");
      for (String assetFile : assetFiles) {
        String assetPath = "inputcontrols/profiles/" + assetFile;
        ControlsProfile originProfile = loadProfile(context, assetManager.open(assetPath));

        File targetFile = null;
        for (File file : files) {
          ControlsProfile targetProfile = loadProfile(context, file);
          if (originProfile.id == targetProfile.id
              && originProfile.getName().equals(targetProfile.getName())) {
            targetFile = file;
            break;
          }
        }

        if (targetFile != null) {
          FileUtils.copy(context, assetPath, targetFile);
        } else {
          FileUtils.copy(context, assetPath, new File(profilesDir, assetFile));
        }
      }
    } catch (IOException e) {
    }
  }

  public void loadProfiles(boolean ignoreTemplates) {
    File profilesDir = InputControlsManager.getProfilesDir(context);
    copyAssetProfilesIfNeeded();

    ArrayList<ControlsProfile> profiles = new ArrayList<>();
    File[] files = profilesDir.listFiles();
    if (files != null) {
      for (File file : files) {
        ControlsProfile profile = loadProfile(context, file);
        if (profile != null) {
          if (!ignoreTemplates || !profile.isTemplate()) {
            profiles.add(profile);
          }
          maxProfileId = Math.max(maxProfileId, profile.id);
        }
      }
    }

    Collections.sort(profiles);
    this.profiles = profiles;
    profilesLoaded = true;
  }

  public ControlsProfile createProfile(String name) {
    if (!profilesLoaded) loadProfiles(false);
    int newId = ++maxProfileId;
    ControlsProfile profile = new ControlsProfile(context, newId);
    profile.setName(name);
    profile.save();
    profiles.add(profile);
    return profile;
  }

  public ControlsProfile duplicateProfile(ControlsProfile source) {
    String newName;
    for (int i = 1; ; i++) {
      newName = source.getName() + " (" + i + ")";
      boolean found = false;
      for (ControlsProfile profile : profiles) {
        if (profile.getName().equals(newName)) {
          found = true;
          break;
        }
      }
      if (!found) break;
    }

    int newId = ++maxProfileId;
    File newFile = ControlsProfile.getProfileFile(context, newId);

    try {
      String jsonStr = FileUtils.readString(ControlsProfile.getProfileFile(context, source.id));
      JSONObject data = new JSONObject(jsonStr != null ? jsonStr : "{}");
      data.put("id", newId);
      data.put("name", newName);
      if (data.has("template")) data.remove("template");
      FileUtils.writeString(newFile, data.toString());
    } catch (JSONException e) {
    }

    ControlsProfile profile = loadProfile(context, newFile);
    profiles.add(profile);
    return profile;
  }

  public void removeProfile(ControlsProfile profile) {
    File file = ControlsProfile.getProfileFile(context, profile.id);
    if (file.isFile() && file.delete()) profiles.remove(profile);
  }

  public synchronized ControlsProfile importProfile(JSONObject data) {
    try {
      if (!profilesLoaded || profiles == null) {
        loadProfiles(false);
      }
      String profileName = data.optString("name", "").trim();
      if (profileName.isEmpty()) {
        Log.e("ICManager", "importProfile: data missing 'name' field: " + data.toString());
        return null;
      }

      ControlsProfile existingProfile = findProfileByName(profileName);
      int targetId = existingProfile != null ? existingProfile.id : ++maxProfileId;
      File targetFile = ControlsProfile.getProfileFile(context, targetId);
      data.put("id", targetId);
      FileUtils.writeString(targetFile, data.toString());
      ControlsProfile newProfile = loadProfile(context, targetFile);

      if (newProfile == null) {
        Log.e("ICManager", "importProfile: loadProfile returned null for " + targetFile.getPath());
        // If writing was successful, still return a basic profile object
        newProfile = new ControlsProfile(context, targetId);
        newProfile.setName(data.optString("name", "Imported Profile"));
      }

      int foundIndex = existingProfile != null ? profiles.indexOf(existingProfile) : -1;

      if (foundIndex != -1) {
        profiles.set(foundIndex, newProfile);
      } else profiles.add(newProfile);
      return newProfile;
    } catch (JSONException e) {
      Log.e("ICManager", "importProfile: JSONException", e);
      return null;
    }
  }

  private ControlsProfile findProfileByName(String name) {
    String normalizedName = normalizeProfileName(name);
    for (ControlsProfile profile : profiles) {
      if (normalizeProfileName(profile.getName()).equals(normalizedName)) {
        return profile;
      }
    }
    return null;
  }

  private String normalizeProfileName(String name) {
    if (name == null) return "";
    String trimmed = name.trim();
    if (trimmed.toLowerCase(Locale.ROOT).endsWith(".icp")) {
      trimmed = trimmed.substring(0, trimmed.length() - 4);
    }
    return trimmed.trim().toLowerCase(Locale.ROOT);
  }

  public File exportProfile(ControlsProfile profile) {
    File destination;
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
    String winlatorPath = sp.getString("winlator_path_uri", null);
    if (winlatorPath != null) {
      Uri winlatorUri = Uri.parse(winlatorPath);
      destination =
          new File(
              FileUtils.getFilePathFromUri(context, winlatorUri),
              "profiles/" + profile.getName() + ".icp");
    } else {
      destination =
          new File(SettingsConfig.DEFAULT_WINLATOR_PATH, "profiles/" + profile.getName() + ".icp");
    }
    FileUtils.copy(ControlsProfile.getProfileFile(context, profile.id), destination);
    MediaScannerConnection.scanFile(
        context, new String[] {destination.getAbsolutePath()}, null, null);
    return destination.isFile() ? destination : null;
  }

  public static ControlsProfile loadProfile(Context context, File file) {
    try {
      return loadProfile(context, new FileInputStream(file));
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  public static ControlsProfile loadProfile(Context context, InputStream inStream) {
    try (JsonReader reader =
        new JsonReader(new InputStreamReader(inStream, StandardCharsets.UTF_8))) {
      int profileId = 0;
      String profileName = null;
      float cursorSpeed = Float.NaN;
      int fieldsRead = 0;

      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();

        if (name.equals("id")) {
          profileId = reader.nextInt();
          fieldsRead++;
        } else if (name.equals("name")) {
          profileName = reader.nextString();
          fieldsRead++;
        } else if (name.equals("cursorSpeed")) {
          cursorSpeed = (float) reader.nextDouble();
          fieldsRead++;
        } else {
          if (fieldsRead == 3) break;
          reader.skipValue();
        }
      }

      ControlsProfile profile = new ControlsProfile(context, profileId);
      if (profileName != null) {
        profile.setName(profileName);
        profile.setCursorSpeed(cursorSpeed);
        reader.close();
        return profile;
      }
      reader.close();
      return null;
    } catch (IOException e) {
      return null;
    }
  }

  public ControlsProfile getProfile(int id) {
    for (ControlsProfile profile : getProfiles()) if (profile.id == id) return profile;
    return null;
  }
}
