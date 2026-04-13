package com.winlator.cmod.runtime.compat.fexcore;

import android.content.Context;
import android.widget.Spinner;
import com.winlator.cmod.R;
import com.winlator.cmod.runtime.content.ContentProfile;
import com.winlator.cmod.runtime.content.ContentsManager;
import com.winlator.cmod.runtime.display.environment.ImageFs;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public abstract class FEXCoreManager {
  private static final String[] APP_CONFIG_EXE_NAMES = {
    "RockstarService.exe",
    "RockstarSteamHelper.exe",
    "SocialClubHelper.exe",
    "UplayWebCore.exe",
    "steamservice.exe",
    "steamwebhelper.exe",
    "steam.exe",
  };

  private static final String APP_CONFIG_CONTENT =
      "{\n"
          + "  \"Config\": {\n"
          + "    \"Multiblock\": \"0\",\n"
          + "    \"X87ReducedPrecision\": \"1\",\n"
          + "    \"VectorTSOEnabled\": \"1\",\n"
          + "    \"HalfBarrierTSOEnabled\": \"1\",\n"
          + "    \"MonoHacks\": \"0\"\n"
          + "  }\n"
          + "}\n";

  public static void loadFEXCoreVersion(
      Context context, ContentsManager contentsManager, Spinner spinner, String fexcoreVersion) {
    String[] originalItems = context.getResources().getStringArray(R.array.fexcore_version_entries);
    List<String> itemList = new ArrayList<>(Arrays.asList(originalItems));
    for (ContentProfile profile :
        contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)) {
      String entryName = ContentsManager.getEntryName(profile);
      int firstDashIndex = entryName.indexOf('-');
      itemList.add(entryName.substring(firstDashIndex + 1));
    }
    AppUtils.setupThemedSpinner(spinner, context, itemList);
    AppUtils.setSpinnerSelectionFromValue(spinner, fexcoreVersion);
  }

  public static File ensureAppConfigOverrides(Context context) {
    try {
      ImageFs imageFs = ImageFs.find(context);
      File rootDir = imageFs.getRootDir();
      File baseDir = new File(rootDir, "/home/xuser/.fex-emu");
      File appConfigDir = new File(baseDir, "AppConfig");
      appConfigDir.mkdirs();

      for (String exeName : APP_CONFIG_EXE_NAMES) {
        File appConfigFile = new File(appConfigDir, exeName + ".json");
        FileUtils.writeString(appConfigFile, APP_CONFIG_CONTENT);
      }

      return baseDir;
    } catch (Exception e) {
      return null;
    }
  }

  public static String findAppConfigExeName(String commandLine) {
    if (commandLine == null || commandLine.isEmpty()) return null;
    String lowerCommand = commandLine.toLowerCase(Locale.ENGLISH);
    for (String exeName : APP_CONFIG_EXE_NAMES) {
      if (lowerCommand.contains(exeName.toLowerCase(Locale.ENGLISH))) {
        return exeName;
      }
    }
    return null;
  }
}
