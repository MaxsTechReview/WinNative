package com.winlator.cmod.inputcontrols;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.JsonReader;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import org.json.JSONException;
import org.json.JSONObject;

public class InputControlsManager {
    private final Context context;
    private int maxProfileId;
    private ArrayList<ControlsProfile> profiles;
    private boolean profilesLoaded = false;

    public InputControlsManager(Context context) {
        this.context = context;
    }

    public static File getProfilesDir(Context context) {
        File profilesDir = new File(context.getFilesDir(), "profiles");
        if (!profilesDir.isDirectory()) {
            profilesDir.mkdir();
        }
        return profilesDir;
    }

    public ArrayList<ControlsProfile> getProfiles() {
        if (!this.profilesLoaded) {
            loadProfiles();
        }
        return this.profiles;
    }

    private void copyAssetProfilesIfNeeded() {
        File profilesDir = getProfilesDir(this.context);
        if (FileUtils.isEmpty(profilesDir)) {
            FileUtils.copy(this.context, "inputcontrols/profiles", profilesDir);
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        int newVersion = AppUtils.getVersionCode(this.context);
        int oldVersion = preferences.getInt("inputcontrols_app_version", 0);
        if (oldVersion != newVersion) {
            preferences.edit().putInt("inputcontrols_app_version", newVersion).apply();
            File[] files = profilesDir.listFiles();
            if (files == null) {
                return;
            }
            try {
                for (String assetFile : this.context.getAssets().list("inputcontrols/profiles")) {
                    String assetPath = "inputcontrols/profiles/" + assetFile;
                    ControlsProfile originProfile = loadProfile(this.context, this.context.getAssets().open(assetPath));
                    File targetFile = null;
                    int length = files.length;
                    int i = 0;
                    while (true) {
                        if (i >= length) {
                            break;
                        }
                        File file = files[i];
                        ControlsProfile targetProfile = loadProfile(this.context, file);
                        if (originProfile.id != targetProfile.id || !originProfile.getName().equals(targetProfile.getName())) {
                            i++;
                        } else {
                            targetFile = file;
                            break;
                        }
                    }
                    if (targetFile != null) {
                        FileUtils.copy(this.context, assetPath, targetFile);
                    } else {
                        FileUtils.copy(this.context, assetPath, new File(profilesDir, assetFile));
                    }
                }
            } catch (IOException e) {
            }
        }
    }

    public void loadProfiles() {
        File profilesDir = getProfilesDir(this.context);
        copyAssetProfilesIfNeeded();
        ArrayList<ControlsProfile> profiles = new ArrayList<>();
        File[] files = profilesDir.listFiles();
        if (files != null) {
            for (File file : files) {
                ControlsProfile profile = loadProfile(this.context, file);
                if (profile != null) {
                    profiles.add(profile);
                    this.maxProfileId = Math.max(this.maxProfileId, profile.id);
                }
            }
        }
        Collections.sort(profiles);
        this.profiles = profiles;
        this.profilesLoaded = true;
    }

    public ControlsProfile createProfile(String name) {
        if (!this.profilesLoaded) {
            loadProfiles();
        }
        int i = this.maxProfileId + 1;
        this.maxProfileId = i;
        ControlsProfile profile = new ControlsProfile(this.context, i);
        profile.setName(name);
        profile.save();
        this.profiles.add(profile);
        return profile;
    }

    public ControlsProfile duplicateProfile(ControlsProfile source) {
        String newName;
        int i = 1;
        while (true) {
            newName = source.getName() + " (" + i + ")";
            boolean found = false;
            Iterator<ControlsProfile> it = this.profiles.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                ControlsProfile profile = it.next();
                if (profile.getName().equals(newName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                break;
            }
            i++;
        }
        int i2 = this.maxProfileId + 1;
        this.maxProfileId = i2;
        File newFile = ControlsProfile.getProfileFile(this.context, i2);
        try {
            JSONObject data = new JSONObject(FileUtils.readString(ControlsProfile.getProfileFile(this.context, source.id)));
            data.put("id", i2);
            data.put("name", newName);
            FileUtils.writeString(newFile, data.toString());
        } catch (JSONException e) {
        }
        ControlsProfile profile2 = loadProfile(this.context, newFile);
        this.profiles.add(profile2);
        return profile2;
    }

    public void removeProfile(ControlsProfile profile) {
        File file = ControlsProfile.getProfileFile(this.context, profile.id);
        if (file.isFile() && file.delete()) {
            this.profiles.remove(profile);
        }
    }

    public static ControlsProfile loadProfile(Context context, File file) {
        try {
            return loadProfile(context, new FileInputStream(file));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static ControlsProfile loadProfile(Context context, InputStream inStream) {
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
            int profileId = 0;
            String profileName = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("id")) {
                    profileId = reader.nextInt();
                } else if (name.equals("name")) {
                    profileName = reader.nextString();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            ControlsProfile profile = new ControlsProfile(context, profileId);
            profile.setName(profileName);
            reader.close();
            return profile;
        } catch (IOException e) {
            return null;
        }
    }

    public ControlsProfile getProfile(int id) {
        Iterator<ControlsProfile> it = getProfiles().iterator();
        while (it.hasNext()) {
            ControlsProfile profile = it.next();
            if (profile.id == id) {
                return profile;
            }
        }
        return null;
    }
}
