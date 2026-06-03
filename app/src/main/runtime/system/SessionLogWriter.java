package com.winlator.cmod.runtime.system;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

public final class SessionLogWriter {
  private static final String TAG = "SessionLogWriter";

  /** Keep only the newest N session files per category to bound storage use. */
  private static final int MAX_SESSION_FILES_PER_CATEGORY = 15;

  private BufferedWriter box64Writer;
  private BufferedWriter fexWriter;
  private BufferedWriter wineWriter;

  private SessionLogWriter() {}

  public static SessionLogWriter create(
      Context context,
      String executable,
      boolean box64LogsEnabled,
      boolean fexLogsEnabled,
      boolean wineDebugEnabled,
      boolean box64Active,
      boolean fexActive) {
    SessionLogWriter writer = new SessionLogWriter();
    try {
      File logsDir = LogManager.getLogsDir(context);
      String exe = sanitize(executable);
      String stamp = DateFormat.format("yyyy-MM-dd_HH-mm-ss", new Date()).toString();

      // Both toggles on but only one emulator runs: route to the active one so
      // we don't create a misleading empty file for the inactive emulator.
      boolean routeBox64 = box64LogsEnabled;
      boolean routeFex = fexLogsEnabled;
      if (box64LogsEnabled && fexLogsEnabled) {
        if (box64Active && !fexActive) {
          routeFex = false;
        } else if (fexActive && !box64Active) {
          routeBox64 = false;
        }
      }

      if (routeBox64) {
        writer.box64Writer = openWriter(logsDir, "box64", exe, stamp);
        prune(logsDir, "box64");
      }
      if (routeFex) {
        writer.fexWriter = openWriter(logsDir, "fexcore", exe, stamp);
        prune(logsDir, "fexcore");
      }
      if (wineDebugEnabled) {
        writer.wineWriter = openWriter(logsDir, "wine", exe, stamp);
        prune(logsDir, "wine");
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to open session log writers", e);
    }
    return writer;
  }

  private static BufferedWriter openWriter(File logsDir, String category, String exe, String stamp)
      throws IOException {
    File file = new File(logsDir, category + "_" + exe + "_" + stamp + ".txt");
    return new BufferedWriter(new FileWriter(file));
  }

  /** Routes one (already timestamped) log line to every open session file. */
  public synchronized void write(String line) {
    writeTo(box64Writer, line);
    writeTo(fexWriter, line);
    writeTo(wineWriter, line);
  }

  private static void writeTo(BufferedWriter writer, String line) {
    if (writer == null) return;
    try {
      writer.write(line);
      writer.write("\n");
    } catch (IOException ignored) {
    }
  }

  public synchronized void flush() {
    flush(box64Writer);
    flush(fexWriter);
    flush(wineWriter);
  }

  private static void flush(BufferedWriter writer) {
    if (writer == null) return;
    try {
      writer.flush();
    } catch (IOException ignored) {
    }
  }

  public synchronized void close() {
    close(box64Writer);
    close(fexWriter);
    close(wineWriter);
    box64Writer = null;
    fexWriter = null;
    wineWriter = null;
  }

  private static void close(BufferedWriter writer) {
    if (writer == null) return;
    try {
      writer.close();
    } catch (IOException ignored) {
    }
  }

  private static String sanitize(String name) {
    if (name == null || name.isEmpty()) return "session";
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    base = base.replaceAll("[^a-zA-Z0-9\\-_]", "_").toLowerCase();
    return base.isEmpty() ? "session" : base;
  }

  /** Deletes the oldest session files for a category beyond the retention cap. */
  private static void prune(File logsDir, String category) {
    File[] files =
        logsDir.listFiles(
            (dir, name) -> name.startsWith(category + "_") && name.endsWith(".txt"));
    if (files == null || files.length <= MAX_SESSION_FILES_PER_CATEGORY) return;
    Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
    for (int i = 0; i < files.length - MAX_SESSION_FILES_PER_CATEGORY; i++) {
      files[i].delete();
    }
  }
}
