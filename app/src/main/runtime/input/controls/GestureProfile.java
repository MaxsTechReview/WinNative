package com.winlator.cmod.runtime.input.controls;

import android.content.Context;
import com.winlator.cmod.runtime.input.ui.TouchGestureConfig;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.File;
import org.json.JSONException;
import org.json.JSONObject;

public class GestureProfile implements Comparable<GestureProfile> {
  public final int id;
  private final Context context;
  private String name = "";
  private String configJson;

  public GestureProfile(Context context, int id) {
    this.context = context;
    this.id = id;
    this.configJson = new TouchGestureConfig().toJson();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getConfigJson() {
    return configJson;
  }

  public void setConfigJson(String json) {
    this.configJson = (json == null || json.trim().isEmpty()) ? new TouchGestureConfig().toJson() : json;
  }

  public static File getProfileFile(Context context, int id) {
    return new File(GestureProfileManager.getProfilesDir(context), "gesture-" + id + ".gcp");
  }

  public void save() {
    try {
      JSONObject o = new JSONObject();
      o.put("id", id);
      o.put("name", name);
      o.put("config", new JSONObject(configJson));
      FileUtils.writeString(getProfileFile(context, id), o.toString());
    } catch (JSONException e) {
    }
  }

  @Override
  public int compareTo(GestureProfile other) {
    return Integer.compare(id, other.id);
  }
}
