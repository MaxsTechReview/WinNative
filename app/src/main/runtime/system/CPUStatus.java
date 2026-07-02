package com.winlator.cmod.runtime.system;

import com.winlator.cmod.shared.io.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public abstract class CPUStatus {
  public static short[] getCurrentClockSpeeds() {
    int numProcessors = Runtime.getRuntime().availableProcessors();
    short[] clockSpeeds = new short[numProcessors];
    for (int i = 0; i < numProcessors; i++) {
      int currFreq =
          FileUtils.readInt("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
      clockSpeeds[i] = (short) (currFreq / 1000);
    }
    return clockSpeeds;
  }

  public static short getMaxClockSpeed(int cpuIndex) {
    int maxFreq =
        FileUtils.readInt("/sys/devices/system/cpu/cpu" + cpuIndex + "/cpufreq/cpuinfo_max_freq");
    return (short) (maxFreq / 1000);
  }

  private static volatile String[] cpuTempPaths;

  /**
   * CPU temperature in whole °C read from the best-matching sysfs thermal zone, or -1 if none is
   * readable. Zone paths are discovered once (ranked by the zone `type` name) and cached; each call
   * then just reads the chosen temp file, converting millidegrees to °C.
   */
  public static int getCpuTempC() {
    String[] paths = cpuTempPaths;
    if (paths == null) {
      paths = discoverCpuTempPaths();
      if (paths.length > 0) cpuTempPaths = paths;
    }
    for (String path : paths) {
      int raw = FileUtils.readInt(path);
      int celsius = raw > 1000 ? (raw + 500) / 1000 : raw;
      if (celsius >= 1 && celsius <= 150) return celsius;
    }
    return -1;
  }

  private static String[] discoverCpuTempPaths() {
    ArrayList<int[]> ranks = new ArrayList<>();
    ArrayList<String> paths = new ArrayList<>();
    File[] roots = {new File("/sys/class/thermal"), new File("/sys/devices/virtual/thermal")};
    for (File root : roots) {
      File[] zones =
          root.listFiles((dir, name) -> name.startsWith("thermal_zone") && new File(dir, name).isDirectory());
      if (zones == null) continue;
      for (File zone : zones) {
        String type = FileUtils.readString(new File(zone, "type"));
        if (type == null) continue;
        int rank = rankCpuZone(type.trim().toLowerCase(Locale.US));
        if (rank < 0) continue;
        String path = new File(zone, "temp").getAbsolutePath();
        if (paths.contains(path)) continue;
        ranks.add(new int[] {rank, paths.size()});
        paths.add(path);
      }
    }
    // Order by rank (best CPU match first), then path for a stable tie-break.
    Collections.sort(
        ranks,
        (a, b) -> a[0] != b[0] ? a[0] - b[0] : paths.get(a[1]).compareTo(paths.get(b[1])));
    String[] ordered = new String[ranks.size()];
    for (int i = 0; i < ranks.size(); i++) ordered[i] = paths.get(ranks.get(i)[1]);
    return ordered;
  }

  private static int rankCpuZone(String type) {
    if (type.contains("cpu-silicon")) return 0;
    if (type.contains("cpu-0")) return 1;
    if (type.contains("cpu") && !type.contains("gpu")) return 2;
    if (type.contains("soc")) return 3;
    if (type.contains("s5p-tmu")) return 4;
    if (type.contains("cputop")) return 5;
    if (type.contains("tsens")) return 6;
    if (type.contains("cluster")) return 7;
    if (type.contains("big") || type.contains("little")) return 8;
    return -1;
  }
}
