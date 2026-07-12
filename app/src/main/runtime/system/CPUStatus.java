package com.winlator.cmod.runtime.system;

import com.winlator.cmod.shared.io.FileUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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

  public static final class CpuSample {
    private final long aggBusy;
    private final long aggIdle;
    private final long[] coreBusy;
    private final long[] coreIdle;

    private CpuSample(long aggBusy, long aggIdle, long[] coreBusy, long[] coreIdle) {
      this.aggBusy = aggBusy;
      this.aggIdle = aggIdle;
      this.coreBusy = coreBusy;
      this.coreIdle = coreIdle;
    }

    public int coreCount() {
      return coreBusy.length;
    }

    public int percentSince(CpuSample prev) {
      if (prev == null) return -1;
      return percent(aggBusy - prev.aggBusy, aggIdle - prev.aggIdle);
    }

    public int corePercentSince(CpuSample prev, int core) {
      if (prev == null || core < 0 || core >= coreBusy.length || core >= prev.coreBusy.length)
        return 0;
      return percent(coreBusy[core] - prev.coreBusy[core], coreIdle[core] - prev.coreIdle[core]);
    }

    private static int percent(long busyDelta, long idleDelta) {
      long total = busyDelta + idleDelta;
      if (total <= 0) return 0;
      long p = (100 * busyDelta) / total;
      if (p < 0) return 0;
      if (p > 100) return 100;
      return (int) p;
    }
  }

  public static CpuSample readCpuSample() {
    int numProcessors = Runtime.getRuntime().availableProcessors();
    long[] coreBusy = new long[numProcessors];
    long[] coreIdle = new long[numProcessors];
    long aggBusy = 0;
    long aggIdle = 0;
    try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.startsWith("cpu")) break;
        String[] f = line.trim().split("\\s+");
        if (f.length < 5) continue;
        long user = parseTick(f, 1);
        long nice = parseTick(f, 2);
        long system = parseTick(f, 3);
        long idle = parseTick(f, 4);
        long iowait = parseTick(f, 5);
        long irq = parseTick(f, 6);
        long softirq = parseTick(f, 7);
        long steal = parseTick(f, 8);
        long idleAll = idle + iowait;
        long busy = user + nice + system + irq + softirq + steal;
        if (f[0].equals("cpu")) {
          aggBusy = busy;
          aggIdle = idleAll;
        } else {
          int idx = parseCoreIndex(f[0]);
          if (idx >= 0 && idx < numProcessors) {
            coreBusy[idx] = busy;
            coreIdle[idx] = idleAll;
          }
        }
      }
    } catch (Exception e) {
      return null;
    }
    return new CpuSample(aggBusy, aggIdle, coreBusy, coreIdle);
  }

  private static long parseTick(String[] fields, int index) {
    if (index >= fields.length) return 0;
    try {
      return Long.parseLong(fields[index]);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static int parseCoreIndex(String label) {
    try {
      return Integer.parseInt(label.substring(3));
    } catch (Exception e) {
      return -1;
    }
  }

  public static int getClockFreqLoadPercent() {
    short[] clocks = getCurrentClockSpeeds();
    if (clocks == null || clocks.length == 0) return -1;
    long cur = 0;
    long max = 0;
    for (int i = 0; i < clocks.length; i++) {
      cur += clocks[i];
      max += getMaxClockSpeed(i);
    }
    if (max <= 0) return -1;
    return clampPercent((int) ((cur * 100) / max));
  }

  public static int getClockFreqCorePercent(int core) {
    short[] clocks = getCurrentClockSpeeds();
    if (clocks == null || core < 0 || core >= clocks.length) return 0;
    int max = getMaxClockSpeed(core);
    if (max <= 0) return 0;
    return clampPercent((int) (((float) clocks[core] / max) * 100.0f));
  }

  private static int clampPercent(int p) {
    if (p < 0) return 0;
    if (p > 100) return 100;
    return p;
  }
}
