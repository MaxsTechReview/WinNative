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
import java.util.concurrent.Executors;

public abstract class ProcessHelper {
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
    return listRunningWineProcesses();
  }

  public static void pauseAllWineProcesses() {
    for (String process : listRunningWineProcesses()) {
      suspendProcess(Integer.parseInt(process));
    }
  }

  public static void resumeAllWineProcesses() {
    for (String process : listRunningWineProcesses()) {
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
    Executors.newSingleThreadExecutor()
        .execute(
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
            });
  }

  private static void createWaitForThread(
      java.lang.Process process, final Callback<Integer> terminationCallback) {
    Executors.newSingleThreadExecutor()
        .execute(
            new Runnable() {
              @Override
              public void run() {
                try {
                  int status = process.waitFor();
                  drainDeadChildren("process waitFor");
                  terminationCallback.call(status);
                } catch (InterruptedException e) {
                  Log.e("ProcessHelper", "Error waiting for process termination", e);
                }
              }
            });
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
      affinityMask |= (int) Math.pow(2, index);
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
      affinityMask |= (int) Math.pow(2, index);
    }
    return affinityMask;
  }

  public static int getAffinityMask(boolean[] cpuList) {
    int affinityMask = 0;
    for (int i = 0; i < cpuList.length; i++) {
      if (cpuList[i]) affinityMask |= (int) Math.pow(2, i);
    }
    return affinityMask;
  }

  public static int getAffinityMask(int from, int to) {
    int affinityMask = 0;
    for (int i = from; i < to; i++) affinityMask |= (int) Math.pow(2, i);
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

    for (int index = 0; index < allPids.length; index++) {
      String statData = "";
      try (FileInputStream fr = new FileInputStream(proc + "/" + allPids[index] + "/stat");
          BufferedReader br = new BufferedReader(new InputStreamReader(fr))) {
        statData = br.readLine();
      } catch (IOException e) {
      }
      String cmdlineData = "";
      try (FileInputStream fr = new FileInputStream(proc + "/" + allPids[index] + "/cmdline")) {
        byte[] bytes = fr.readAllBytes();
        cmdlineData = new String(bytes, StandardCharsets.UTF_8).replace('\0', ' ');
      } catch (IOException e) {
      }

      String normalized = (statData + " " + cmdlineData).toLowerCase();
      for (String filter : SESSION_PROCESS_FILTERS) {
        if (normalized.contains(filter) && !filteredPids.contains(allPids[index])) {
          filteredPids.add(allPids[index]);
        }
      }
    }
    return filteredPids;
  }
}
