package com.winlator.cmod.runtime.container;

import android.content.Context;
import androidx.annotation.Nullable;
import com.winlator.cmod.runtime.content.ContentProfile;
import com.winlator.cmod.runtime.content.ContentsManager;
import com.winlator.cmod.runtime.wine.WineInfo;
import java.util.Collection;

public final class ContainerNameUtils {
  private ContainerNameUtils() {}

  public static String buildVersionBasedName(
      Context context, ContentsManager contentsManager, @Nullable String wineVersion) {
    if (wineVersion == null || wineVersion.trim().isEmpty()) {
      return "Container";
    }

    if (contentsManager != null) {
      ContentProfile profile = contentsManager.getProfileByEntryName(wineVersion);
      if (profile != null && profile.verName != null && !profile.verName.trim().isEmpty()) {
        return profile.verName.trim();
      }
    }

    WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion);
    return wineInfo.toString().trim();
  }

  public static String buildUniqueVersionBasedName(
      Context context,
      ContentsManager contentsManager,
      @Nullable String wineVersion,
      Collection<Container> existingContainers,
      @Nullable Integer excludeContainerId) {
    String baseName = buildVersionBasedName(context, contentsManager, wineVersion);
    if (baseName.isEmpty()) {
      baseName = "Container";
    }

    String uniqueName = baseName;
    int suffix = 2;
    while (containsName(existingContainers, uniqueName, excludeContainerId)) {
      uniqueName = baseName + " " + suffix;
      suffix++;
    }
    return uniqueName;
  }

  public static boolean isUnspecifiedName(@Nullable String name, int containerId) {
    if (name == null || name.trim().isEmpty()) {
      return true;
    }
    return ("Container-" + containerId).equals(name.trim());
  }

  private static boolean containsName(
      Collection<Container> containers, String name, @Nullable Integer excludeContainerId) {
    for (Container container : containers) {
      if (excludeContainerId != null && container.id == excludeContainerId) {
        continue;
      }
      String existingName = container.getName();
      if (existingName != null && existingName.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }
}
