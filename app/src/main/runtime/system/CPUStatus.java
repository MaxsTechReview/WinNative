package com.winlator.cmod.runtime.system;

import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

  private static final long CLOCK_TICKS_PER_SEC = Os.sysconf(OsConstants._SC_CLK_TCK);
  private static final int TOTAL_CORES = readTotalCores();

  public static final class AppCpuSample {
    private final HashMap<Integer, Long> perPid;
    private final long nanos;

    private AppCpuSample(HashMap<Integer, Long> perPid, long nanos) {
      this.perPid = perPid;
      this.nanos = nanos;
    }

    public int percentSince(AppCpuSample prev) {
      if (prev == null) return -1;
      long deltaTicks = 0;
      for (Map.Entry<Integer, Long> e : perPid.entrySet()) {
        Long old = prev.perPid.get(e.getKey());
        if (old != null && e.getValue() >= old) deltaTicks += e.getValue() - old;
      }
      double deltaSec = (nanos - prev.nanos) / 1e9;
      if (deltaSec <= 0) return -1;
      double pct = 100.0 * deltaTicks / (deltaSec * CLOCK_TICKS_PER_SEC * TOTAL_CORES);
      if (pct < 0) return 0;
      if (pct > 100) return 100;
      return (int) Math.round(pct);
    }
  }

  public static AppCpuSample readAppCpuSample() {
    String[] names = new File("/proc").list();
    if (names == null) return null;
    int myUid = Process.myUid();
    HashMap<Integer, Long> cur = new HashMap<>();
    for (String name : names) {
      if (name.isEmpty() || !Character.isDigit(name.charAt(0))) continue;
      int pid;
      try {
        pid = Integer.parseInt(name);
      } catch (NumberFormatException e) {
        continue;
      }
      String dir = "/proc/" + name;
      try {
        if (Os.stat(dir).st_uid != myUid) continue;
      } catch (Exception e) {
        continue;
      }
      try (BufferedReader reader = new BufferedReader(new FileReader(dir + "/stat"))) {
        String line = reader.readLine();
        if (line == null) continue;
        int close = line.lastIndexOf(')');
        if (close < 0) continue;
        String[] f = line.substring(close + 2).trim().split("\\s+");
        if (f.length < 13) continue;
        cur.put(pid, Long.parseLong(f[11]) + Long.parseLong(f[12]));
      } catch (Exception ignored) {
      }
    }
    return new AppCpuSample(cur, SystemClock.elapsedRealtimeNanos());
  }

  private static int readTotalCores() {
    try (BufferedReader reader =
        new BufferedReader(new FileReader("/sys/devices/system/cpu/possible"))) {
      String s = reader.readLine();
      if (s != null) {
        s = s.trim();
        int dash = s.indexOf('-');
        int n = dash >= 0 ? Integer.parseInt(s.substring(dash + 1)) + 1 : Integer.parseInt(s) + 1;
        if (n > 0) return n;
      }
    } catch (Exception ignored) {
    }
    int n = Runtime.getRuntime().availableProcessors();
    return n > 0 ? n : 1;
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
