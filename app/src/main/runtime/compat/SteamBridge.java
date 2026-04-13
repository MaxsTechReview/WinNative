package com.winlator.cmod.runtime.compat;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Method;

/**
 * Java bridge to call Kotlin Steam classes via reflection, avoiding KSP NullPointerException when
 * scanning Java files that import Kotlin companion objects.
 */
public class SteamBridge {
  private static final String TAG = "SteamBridge";

  public static String getAppDirPath(int appId) {
    try {
      Class<?> companion =
          Class.forName("com.winlator.cmod.feature.stores.steam.service.SteamService$Companion");
      Object instance =
          Class.forName("com.winlator.cmod.feature.stores.steam.service.SteamService")
              .getField("Companion")
              .get(null);
      Method method = companion.getMethod("getAppDirPath", int.class);
      return (String) method.invoke(instance, appId);
    } catch (Exception e) {
      Log.e(TAG, "Failed to call SteamService.getAppDirPath", e);
      return "";
    }
  }

  public static String getInstalledExe(int appId) {
    try {
      Class<?> companion =
          Class.forName("com.winlator.cmod.feature.stores.steam.service.SteamService$Companion");
      Object instance =
          Class.forName("com.winlator.cmod.feature.stores.steam.service.SteamService")
              .getField("Companion")
              .get(null);
      Method method = companion.getMethod("getInstalledExe", int.class);
      return (String) method.invoke(instance, appId);
    } catch (Exception e) {
      Log.e(TAG, "Failed to call SteamService.getInstalledExe", e);
      return "";
    }
  }

  public static boolean extractSteam(Context context) {
    try {
      Class<?> clazz = Class.forName("com.winlator.cmod.feature.stores.steam.SteamClientManager");
      Object instance = clazz.getField("INSTANCE").get(null);
      Method method = clazz.getMethod("extractSteam", Context.class);
      return (Boolean) method.invoke(instance, context);
    } catch (Exception e) {
      Log.e(TAG, "Failed to call SteamClientManager.extractSteam", e);
      return false;
    }
  }

  public static boolean forceExtractSteam(Context context) {
    try {
      Class<?> clazz = Class.forName("com.winlator.cmod.feature.stores.steam.SteamClientManager");
      Object instance = clazz.getField("INSTANCE").get(null);
      Method method = clazz.getMethod("forceExtractSteam", Context.class);
      return (Boolean) method.invoke(instance, context);
    } catch (Exception e) {
      Log.e(TAG, "Failed to call SteamClientManager.forceExtractSteam", e);
      return false;
    }
  }

  public static boolean isSteamDownloaded(Context context) {
    try {
      Class<?> clazz = Class.forName("com.winlator.cmod.feature.stores.steam.SteamClientManager");
      Object instance = clazz.getField("INSTANCE").get(null);
      Method method = clazz.getMethod("isSteamDownloaded", Context.class);
      return (Boolean) method.invoke(instance, context);
    } catch (Exception e) {
      Log.e(TAG, "Failed to call SteamClientManager.isSteamDownloaded", e);
      return false;
    }
  }

  public static boolean isSteamInstalled(Context context) {
    try {
      Class<?> clazz = Class.forName("com.winlator.cmod.feature.stores.steam.SteamClientManager");
      Object instance = clazz.getField("INSTANCE").get(null);
      Method method = clazz.getMethod("isSteamInstalled", Context.class);
      return (Boolean) method.invoke(instance, context);
    } catch (Exception e) {
      Log.e(TAG, "Failed to call SteamClientManager.isSteamInstalled", e);
      return false;
    }
  }

  /** Downloads steam.tzst if missing, then extracts it. Blocking call. */
  public static boolean ensureSteamReady(Context context) {
    try {
      Class<?> clazz = Class.forName("com.winlator.cmod.feature.stores.steam.SteamClientManager");
      Object instance = clazz.getField("INSTANCE").get(null);
      Method method = clazz.getMethod("ensureSteamReady", Context.class);
      return (Boolean) method.invoke(instance, context);
    } catch (Exception e) {
      Log.e(TAG, "Failed to call SteamClientManager.ensureSteamReady", e);
      return false;
    }
  }

  /** Downloads steam-token.tzst if missing, then extracts it. Blocking call. */
  public static boolean ensureRealSteamSupportReady(Context context) {
    try {
      Class<?> clazz = Class.forName("com.winlator.cmod.feature.stores.steam.SteamClientManager");
      Object instance = clazz.getField("INSTANCE").get(null);
      Method method = clazz.getMethod("ensureRealSteamSupportReady", Context.class);
      return (Boolean) method.invoke(instance, context);
    } catch (Exception e) {
      Log.e(TAG, "Failed to call SteamClientManager.ensureRealSteamSupportReady", e);
      return false;
    }
  }

  /** Downloads experimental-drm if missing, then extracts it. Blocking call. */
  public static boolean ensureColdClientSupportReady(Context context) {
    try {
      Class<?> clazz = Class.forName("com.winlator.cmod.feature.stores.steam.SteamClientManager");
      Object instance = clazz.getField("INSTANCE").get(null);
      Method method = clazz.getMethod("ensureColdClientSupportReady", Context.class);
      return (Boolean) method.invoke(instance, context);
    } catch (Exception e) {
      Log.e(TAG, "Failed to call SteamClientManager.ensureColdClientSupportReady", e);
      return false;
    }
  }

  /**
   * Get the encrypted app ticket as base64 string for the given appId. Requires an active Steam
   * login. Returns null if not logged in or ticket unavailable.
   */
  public static String getEncryptedAppTicketBase64(int appId) {
    try {
      Class<?> clazz = Class.forName("com.winlator.cmod.feature.stores.steam.SteamClientManager");
      Object instance = clazz.getField("INSTANCE").get(null);
      Method method = clazz.getMethod("getEncryptedAppTicketBase64Blocking", int.class);
      return (String) method.invoke(instance, appId);
    } catch (Exception e) {
      Log.d(TAG, "getEncryptedAppTicketBase64 not available: " + e.getMessage());
      return null;
    }
  }

  /** Check if the user is logged into Steam. */
  public static boolean isLoggedIn() {
    try {
      Class<?> serviceClass =
          Class.forName("com.winlator.cmod.feature.stores.steam.service.SteamService");
      Class<?> companion =
          Class.forName("com.winlator.cmod.feature.stores.steam.service.SteamService$Companion");
      Object companionInstance = serviceClass.getField("Companion").get(null);
      Method method = companion.getMethod("isLoggedIn");
      return (Boolean) method.invoke(companionInstance);
    } catch (Exception e) {
      Log.d(TAG, "isLoggedIn check failed: " + e.getMessage());
      return false;
    }
  }
}
