package com.winlator.cmod.runtime.system;

import android.os.Process;
import android.util.Log;
import com.winlator.cmod.shared.util.Callback;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public abstract class ProcessHelper {
  private static final String TAG = "ProcessHelper";
  private static final int MAX_PROCESS_DETAIL_LENGTH = 240;
  public static final boolean PRINT_DEBUG = false;
  private static final ArrayList<Callback<String>> debugCallbacks = new ArrayList<>();
  private static final String[] SESSION_PROCESS_FILTERS = {
    "wine",
    "wine64",
    "wineserver",
    "winedevice",
    "services.exe",
    "start.exe",
    "rpcss.exe",
    "conhost.exe",
    "box64",
    "box86",
    "fexcore",
    "wowbox64",
    "winhandler",
    "wfm.exe",
    "explorer.exe",
    "steam.exe",
    "gameoverlayui",
    ".exe"
  };
  private static final byte SIGCONT = 18;
  private static final byte SIGSTOP = 19;
  private static final byte SIGTERM = 15;
  private static final byte SIGKILL = 9;

  static {
    try {
      System.loadLibrary("winlator");
    } catch (UnsatisfiedLinkError e) {
      Log.w(
          "ProcessHelper",
          "winlator native library not available for explicit child reaping yet",
          e);
    }
  }

  public static native int reapDeadChildrenNow();

  public static native void startNativeReaperWindow(int durationMs);

  public static void drainDeadChildren(String reason) {
    try {
      int reaped = reapDeadChildrenNow();
      if (reaped > 0) {
        Log.i("ProcessHelper", "Reaped " + reaped + " dead child processes after " + reason);
      }
    } catch (UnsatisfiedLinkError e) {
      Log.w("ProcessHelper", "Failed to explicitly reap dead children after " + reason, e);
    }
  }

  public static void scheduleDeadChildReapSweep(String reason, long durationMs, long intervalMs) {
    if (durationMs <= 0) return;
    try {
      startNativeReaperWindow((int) durationMs);
      Log.d(
          "ProcessHelper",
          "Started native reaper window after " + reason + " for " + durationMs + "ms");
    } catch (UnsatisfiedLinkError e) {
      Log.w("ProcessHelper", "Failed to start native reaper window after " + reason, e);
    }
  }

  public static void suspendProcess(int pid) {
    Process.sendSignal(pid, SIGSTOP);
    if (PRINT_DEBUG) Log.d("ProcessHelper", "Process suspended with pid: " + pid);
  }

  public static void resumeProcess(int pid) {
    Process.sendSignal(pid, SIGCONT);
    if (PRINT_DEBUG) Log.d("ProcessHelper", "Process resumed with pid: " + pid);
  }

  public static void terminateProcess(int pid) {
    Process.sendSignal(pid, SIGTERM);
    if (PRINT_DEBUG) Log.d("ProcessHelper", "Process terminated with pid: " + pid);
  }

  public static void killProcess(int pid) {
    Process.sendSignal(pid, SIGKILL);
    if (PRINT_DEBUG) Log.d("ProcessHelper", "Process killed with pid: " + pid);
  }

  public static void terminateAllWineProcesses() {
    for (String process : listRunningWineProcesses()) {
      terminateProcess(Integer.parseInt(process));
    }
  }

  public static void forceKillAllWineProcesses() {
    for (String process : listRunningWineProcesses()) {
      killProcess(Integer.parseInt(process));
    }
  }

  public static ArrayList<String> terminateSessionProcessesAndWait(
      long timeoutMs, boolean forceKillAfterTimeout) {
    drainDeadChildren("pre-terminate sweep");
    ArrayList<String> before = listRunningWineProcessDetails();
    if (before.isEmpty()) {
      Log.d(TAG, "No session processes found before termination");
    } else {
      Log.w(TAG, "Terminating session processes: " + before);
    }
    resumeAllWineProcesses();
    terminateAllWineProcesses();
    long start = System.currentTimeMillis();
    ArrayList<String> remaining = listRunningWineProcesses();
    while (!remaining.isEmpty() && System.currentTimeMillis() - start < timeoutMs) {
      drainDeadChildren("terminate wait loop");
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      remaining = listRunningWineProcesses();
    }

    if (!remaining.isEmpty() && forceKillAfterTimeout) {
      Log.w(TAG, "Session processes still alive after SIGTERM: " + listRunningWineProcessDetails());
      forceKillAllWineProcesses();
      long forceKillStart = System.currentTimeMillis();
      while (!(remaining = listRunningWineProcesses()).isEmpty()
          && System.currentTimeMillis() - forceKillStart < 1000) {
        drainDeadChildren("force-kill wait loop");
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    drainDeadChildren("post-terminate sweep");
    ArrayList<String> finalRemaining = listRunningWineProcesses();
    if (finalRemaining.isEmpty()) {
      Log.i(TAG, "Session process cleanup finished with no remaining Wine/session processes");
    } else {
      Log.e(TAG, "Session process cleanup left remaining processes: " + listRunningWineProcessDetails());
    }
    return finalRemaining;
  }

  public static void pauseAllWineProcesses() {
    ArrayList<String> processes = listRunningWineProcesses();
    if (!processes.isEmpty()) Log.d(TAG, "Pausing session processes: " + processes);
    for (String process : processes) {
      suspendProcess(Integer.parseInt(process));
    }
  }

  public static void resumeAllWineProcesses() {
    ArrayList<String> processes = listRunningWineProcesses();
    if (!processes.isEmpty()) Log.d(TAG, "Resuming session processes: " + processes);
    for (String process : processes) {
      resumeProcess(Integer.parseInt(process));
    }
  }

  public static int exec(String command) {
    return exec(command, null);
  }

  public static int exec(String command, String[] envp) {
    return exec(command, envp, null);
  }

  public static int exec(String command, String[] envp, File workingDir) {
    return exec(command, envp, workingDir, null);
  }

  public static int exec(
      String command, String[] envp, File workingDir, Callback<Integer> terminationCallback) {
    if (PRINT_DEBUG) Log.d("ProcessHelper", "env: " + Arrays.toString(envp) + "\ncmd: " + command);

    // Store env vars for future use
    EnvironmentManager.setEnvVars(envp);

    int pid = -1;
    try {
      if (PRINT_DEBUG) Log.d("ProcessHelper", "Splitting command: " + command);
      String[] splitCommand = splitCommand(command);
      if (PRINT_DEBUG)
        Log.d("ProcessHelper", "Split command result: " + Arrays.toString(splitCommand));
      if (PRINT_DEBUG) Log.d("ProcessHelper", "Starting process...");
      ProcessBuilder pb = new ProcessBuilder(splitCommand);
      pb.directory(workingDir);
      pb.environment().putAll(EnvironmentManager.getEnvVars());
      if (debugCallbacks.isEmpty()) {
        File nullFile = new File("/dev/null");
        pb.redirectError(nullFile);
        pb.redirectOutput(nullFile);
      }
      java.lang.Process process = pb.start();
      if (!debugCallbacks.isEmpty()) {
        createDebugThread(process.getInputStream());
        createDebugThread(process.getErrorStream());
      }

      // Accessing hidden field
      if (PRINT_DEBUG) Log.d("ProcessHelper", "Accessing hidden field to get PID");
      Field pidField = process.getClass().getDeclaredField("pid");
      pidField.setAccessible(true);
      pid = pidField.getInt(process);
      pidField.setAccessible(false);
      if (PRINT_DEBUG) Log.d("ProcessHelper", "Process started with pid: " + pid);

      if (terminationCallback != null) createWaitForThread(process, terminationCallback);

    } catch (Exception e) {
      Log.e("ProcessHelper", "Error executing command: " + command, e);
    }
    return pid;
  }

  private static void createDebugThread(final InputStream inputStream) {
    new Thread(
            () -> {
              try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  synchronized (debugCallbacks) {
                    if (!debugCallbacks.isEmpty()) {
                      if (PRINT_DEBUG) System.out.println(line);
                      for (Callback<String> callback : debugCallbacks) callback.call(line);
                    }
                  }
                }
              } catch (IOException e) {
                Log.e("ProcessHelper", "Error in debug thread", e);
              }
            },
            "ProcessDebugReader")
        .start();
  }

  private static void createWaitForThread(
      java.lang.Process process, final Callback<Integer> terminationCallback) {
    new Thread(
            () -> {
              try {
                int status = process.waitFor();
                drainDeadChildren("process waitFor");
                terminationCallback.call(status);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e("ProcessHelper", "Error waiting for process termination", e);
              }
            },
            "ProcessWaitFor")
        .start();
  }

  public static void removeAllDebugCallbacks() {
    synchronized (debugCallbacks) {
      debugCallbacks.clear();
      if (PRINT_DEBUG) Log.d("ProcessHelper", "All debug callbacks removed");
    }
  }

  public static void addDebugCallback(Callback<String> callback) {
    synchronized (debugCallbacks) {
      if (!debugCallbacks.contains(callback)) debugCallbacks.add(callback);
      if (PRINT_DEBUG) Log.d("ProcessHelper", "Added debug callback: " + callback.toString());
    }
  }

  public static void removeDebugCallback(Callback<String> callback) {
    synchronized (debugCallbacks) {
      debugCallbacks.remove(callback);
      if (PRINT_DEBUG) Log.d("ProcessHelper", "Removed debug callback: " + callback.toString());
    }
  }

  public static String[] splitCommand(String command) {
    ArrayList<String> result = new ArrayList<>();
    boolean startedQuotes = false;
    String value = "";
    char currChar, nextChar;
    for (int i = 0, count = command.length(); i < count; i++) {
      currChar = command.charAt(i);
      char quoteChar = '"';

      if (startedQuotes) {
        if (currChar == quoteChar) {
          startedQuotes = false;
          if (!value.isEmpty()) {
            value += quoteChar;
            result.add(value);
            value = "";
          }
        } else value += currChar;
      } else if (currChar == '"' || currChar == '\'') {
        if (currChar == '\'') quoteChar = '\'';
        startedQuotes = true;
        value += quoteChar;
      } else {
        nextChar = i < count - 1 ? command.charAt(i + 1) : '\0';
        if (currChar == ' ' || (currChar == '\\' && nextChar == ' ')) {
          if (currChar == '\\') {
            value += ' ';
            i++;
          } else if (!value.isEmpty()) {
            result.add(value);
            value = "";
          }
        } else {
          value += currChar;
          if (i == count - 1) {
            result.add(value);
            value = "";
          }
        }
      }
    }

    return result.toArray(new String[0]);
  }

  public static String getAffinityMaskAsHexString(String cpuList) {
    String[] values = cpuList.split(",");
    int affinityMask = 0;
    for (String value : values) {
      byte index = Byte.parseByte(value);
      if (index >= 0 && index < Integer.SIZE) affinityMask |= 1 << index;
    }
    return Integer.toHexString(affinityMask);
  }

  public static int getAffinityMask(String cpuList) {
    if (cpuList == null || cpuList.isEmpty()) return 0;
    String[] values = cpuList.split(",");
    int affinityMask = 0;
    for (String value : values) {
      String v = value.trim().replaceAll("[^0-9]", "");
      if (v.isEmpty()) continue;
      byte index = Byte.parseByte(v);
      if (index >= 0 && index < Integer.SIZE) affinityMask |= 1 << index;
    }
    return affinityMask;
  }

  public static int getAffinityMask(boolean[] cpuList) {
    int affinityMask = 0;
    for (int i = 0; i < cpuList.length; i++) {
      if (i >= Integer.SIZE) break;
      if (cpuList[i]) affinityMask |= 1 << i;
    }
    return affinityMask;
  }

  public static int getAffinityMask(int from, int to) {
    int affinityMask = 0;
    for (int i = Math.max(0, from); i < to && i < Integer.SIZE; i++) affinityMask |= 1 << i;
    return affinityMask;
  }

  public static ArrayList<String> listRunningWineProcesses() {
    File proc = new File("/proc");
    String[] allPids;
    ArrayList<String> filteredPids = new ArrayList<String>();
    allPids =
        proc.list(
            new FilenameFilter() {
              public boolean accept(File proc, String filename) {
                return new File(proc, filename).isDirectory() && filename.matches("[0-9]+");
              }
            });

    if (allPids == null) {
      return filteredPids;
    }

    for (String pid : allPids) {
      String statData = readProcStat(proc, pid);
      String cmdlineData = readProcCmdline(proc, pid);
      String normalized = (statData + " " + cmdlineData).toLowerCase();
      if (isSessionProcess(normalized) && !filteredPids.contains(pid)) filteredPids.add(pid);
    }
    return filteredPids;
  }

  public static ArrayList<String> listRunningWineProcessDetails() {
    File proc = new File("/proc");
    String[] allPids =
        proc.list(
            new FilenameFilter() {
              public boolean accept(File proc, String filename) {
                return new File(proc, filename).isDirectory() && filename.matches("[0-9]+");
              }
            });
    ArrayList<String> details = new ArrayList<>();
    if (allPids == null) return details;

    for (String pid : allPids) {
      String statData = readProcStat(proc, pid);
      String cmdlineData = readProcCmdline(proc, pid);
      String normalized = (statData + " " + cmdlineData).toLowerCase();
      if (!isSessionProcess(normalized)) continue;

      String name = getStatProcessName(statData);
      String command = cmdlineData.trim();
      String detail =
          pid
              + " "
              + (!name.isEmpty() ? name : "<unknown>")
              + (!command.isEmpty() ? " :: " + command : "");
      details.add(trimProcessDetail(detail));
    }
    return details;
  }

  private static boolean isSessionProcess(String normalizedProcessData) {
    for (String filter : SESSION_PROCESS_FILTERS) {
      if (normalizedProcessData.contains(filter)) return true;
    }
    return false;
  }

  private static String readProcStat(File proc, String pid) {
    try (FileInputStream fr = new FileInputStream(proc + "/" + pid + "/stat");
        BufferedReader br = new BufferedReader(new InputStreamReader(fr))) {
      String line = br.readLine();
      return line != null ? line : "";
    } catch (IOException e) {
      return "";
    }
  }

  private static String readProcCmdline(File proc, String pid) {
    try (FileInputStream fr = new FileInputStream(proc + "/" + pid + "/cmdline")) {
      byte[] bytes = fr.readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8).replace('\0', ' ');
    } catch (IOException e) {
      return "";
    }
  }

  private static String getStatProcessName(String statData) {
    int start = statData.indexOf('(');
    int end = statData.lastIndexOf(')');
    if (start < 0 || end <= start) return "";
    return statData.substring(start + 1, end);
  }

  private static String trimProcessDetail(String detail) {
    if (detail.length() <= MAX_PROCESS_DETAIL_LENGTH) return detail;
    return detail.substring(0, MAX_PROCESS_DETAIL_LENGTH - 3) + "...";
  }
}
