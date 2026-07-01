package com.winlator.cmod.runtime.display.environment.components;

import android.content.Context;
import android.media.AudioManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;
import com.winlator.cmod.runtime.display.connector.UnixSocketConfig;
import com.winlator.cmod.runtime.display.environment.EnvironmentComponent;
import com.winlator.cmod.runtime.system.ProcessHelper;
import com.winlator.cmod.runtime.wine.EnvVars;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

public class PulseAudioComponent extends EnvironmentComponent {
  private static final String TAG = "PulseAudioComponent";
  private static final String SINK_NAME = "AAudioSink";

  // --- PulseAudio native protocol (talk to module-native-protocol-unix directly) ---
  // The daemon loads module-native-protocol-unix with auth-anonymous=1, so we can drive it
  // over its unix socket with a tiny hand-rolled client. This avoids shipping a `pactl`
  // binary (never bundled) and avoids the glibc/PA-17 CLI modules that cannot load into the
  // bionic/PA-13 host daemon. A suspend->unsuspend toggle forces module-aaudio-sink to close
  // and reopen its AAudio stream, which is what recovers output after a phone call (during the
  // call Android disconnects the app's AAudio output stream and it never re-opens on its own).
  private static final int PA_COMMAND_AUTH = 8;
  private static final int PA_COMMAND_SUSPEND_SINK = 69;
  private static final int PA_NATIVE_COOKIE_LENGTH = 256;
  private static final int PA_PROTOCOL_VERSION = 33; // PulseAudio 13.x; server negotiates down if older
  private static final long PA_INVALID_INDEX = 0xFFFFFFFFL;

  private static final byte TAG_U32 = 'L';
  private static final byte TAG_STRING = 't';
  private static final byte TAG_STRING_NULL = 'N';
  private static final byte TAG_ARBITRARY = 'x';
  private static final byte TAG_BOOLEAN_TRUE = '1';
  private static final byte TAG_BOOLEAN_FALSE = '0';

  private final UnixSocketConfig socketConfig;
  private final Options options;
  private static int pid = -1;
  private static final Object lock = new Object();
  private boolean isPaused = false;
  // Bumped whenever the sink lifecycle changes (suspend/resume/start/stop) so a stale
  // reopen watchdog started by an earlier resume() cancels itself instead of fighting.
  private static int reopenGeneration = 0;

  public PulseAudioComponent(UnixSocketConfig socketConfig) {
    this(socketConfig, new Options());
  }

  public PulseAudioComponent(UnixSocketConfig socketConfig, Options options) {
    this.socketConfig = socketConfig;
    this.options = options != null ? options : new Options();
  }

  public static class Options {
    public static final int DEFAULT_LATENCY_MILLIS = 144;
    public static final int DEFAULT_FRAGMENT_MILLIS = 10;
    public static final int DEFAULT_SAMPLE_RATE = 48000;
    public static final int DEFAULT_ALTERNATE_SAMPLE_RATE = 44100;
    public static final int DEFAULT_CHANNELS = 2;
    public static final float DEFAULT_VOLUME = 1.0f;
    public static final float MAX_VOLUME = 2.0f;
    public static final String PERFORMANCE_MODE_NONE = "none";
    public static final String PERFORMANCE_MODE_POWER_SAVING = "power_saving";
    public static final String PERFORMANCE_MODE_LOW_LATENCY = "low_latency";

    public int latencyMillis = DEFAULT_LATENCY_MILLIS;
    public int fragmentMillis = DEFAULT_FRAGMENT_MILLIS;
    public int sampleRate = DEFAULT_SAMPLE_RATE;
    public int alternateSampleRate = DEFAULT_ALTERNATE_SAMPLE_RATE;
    public int channels = DEFAULT_CHANNELS;
    public float volume = DEFAULT_VOLUME;
    public String performanceMode = PERFORMANCE_MODE_NONE;
    public boolean sampleRateOverridden = false;
    public boolean alternateSampleRateOverridden = false;

    public static Options fromEnvVars(EnvVars envVars) {
      Options options = new Options();
      if (envVars == null) return options;

      options.latencyMillis =
          Math.max(
              0,
              parseInt(
                  firstNonEmpty(
                      envVars.get("WINNATIVE_PULSE_LATENCY_MS"),
                      envVars.get("PULSE_LATENCY_MSEC")),
                  DEFAULT_LATENCY_MILLIS));
      options.fragmentMillis =
          Math.max(
              1,
              parseInt(
                  firstNonEmpty(
                      envVars.get("WINNATIVE_PULSE_FRAGMENT_MS"),
                      envVars.get("ANDROID_PULSE_FRAGMENT_MS")),
                  DEFAULT_FRAGMENT_MILLIS));
      String sampleRate =
          firstNonEmpty(
              envVars.get("WINNATIVE_PULSE_SAMPLE_RATE"),
              envVars.get("ANDROID_PULSE_SAMPLE_RATE"));
      options.sampleRateOverridden = !sampleRate.isEmpty();
      options.sampleRate =
          Math.max(
              8000,
              parseInt(sampleRate, DEFAULT_SAMPLE_RATE));

      String alternateSampleRate =
          firstNonEmpty(
              envVars.get("WINNATIVE_PULSE_ALTERNATE_SAMPLE_RATE"),
              envVars.get("ANDROID_PULSE_ALTERNATE_SAMPLE_RATE"));
      options.alternateSampleRateOverridden = !alternateSampleRate.isEmpty();
      options.alternateSampleRate =
          Math.max(
              8000,
              parseInt(alternateSampleRate, DEFAULT_ALTERNATE_SAMPLE_RATE));
      options.channels =
          Math.max(
              1,
              Math.min(
                  2,
                  parseInt(
                      firstNonEmpty(
                          envVars.get("WINNATIVE_PULSE_CHANNELS"),
                          envVars.get("ANDROID_PULSE_CHANNELS")),
                      DEFAULT_CHANNELS)));
      options.volume =
          Math.max(
              0.0f,
              Math.min(
                  parseFloat(
                      firstNonEmpty(
                          envVars.get("WINNATIVE_PULSE_VOLUME"),
                          envVars.get("ANDROID_PULSE_VOLUME")),
                      DEFAULT_VOLUME),
                  MAX_VOLUME));

      String performanceMode =
          firstNonEmpty(
              envVars.get("WINNATIVE_PULSE_AAUDIO_PERFORMANCE_MODE"),
              envVars.get("ANDROID_PULSE_AAUDIO_PERFORMANCE_MODE"));
      if (performanceMode.equalsIgnoreCase(PERFORMANCE_MODE_LOW_LATENCY)
          || performanceMode.equals("12")) {
        options.performanceMode = PERFORMANCE_MODE_LOW_LATENCY;
      } else if (performanceMode.equalsIgnoreCase(PERFORMANCE_MODE_POWER_SAVING)
          || performanceMode.equals("11")) {
        options.performanceMode = PERFORMANCE_MODE_POWER_SAVING;
      } else {
        options.performanceMode = PERFORMANCE_MODE_NONE;
      }

      return options;
    }

    private static String firstNonEmpty(String first, String second) {
      return first != null && !first.isEmpty() ? first : (second != null ? second : "");
    }

    private static int parseInt(String value, int fallback) {
      try {
        if (value != null && !value.isEmpty()) return Integer.parseInt(value);
      } catch (NumberFormatException ignored) {
      }
      return fallback;
    }

    private static float parseFloat(String value, float fallback) {
      try {
        if (value != null && !value.isEmpty()) return Float.parseFloat(value);
      } catch (NumberFormatException ignored) {
      }
      return fallback;
    }
  }

  @Override
  public void start() {
    synchronized (lock) {
      reopenGeneration++;
      if (isServerRunning()) {
        isPaused = false;
        return;
      }
      killAllPulseAudioProcesses();
      isPaused = false;
      pid = execPulseAudio();
    }
  }

  @Override
  public void stop() {
    synchronized (lock) {
      reopenGeneration++;
      // Best-effort: suspend so module-aaudio-sink releases the AAudio device before we kill it.
      sendNativeCommands(buildSuspendSinkPacket(1, SINK_NAME, true));
      killAllPulseAudioProcesses();
      pid = -1;
      isPaused = false;
    }
  }

  public void suspend() {
    synchronized (lock) {
      if (isPaused) return;
      reopenGeneration++; // cancel any pending reopen watchdog from a prior resume()
      if (isServerRunning()) {
        isPaused = true;
        sendNativeCommands(buildSuspendSinkPacket(1, SINK_NAME, true));
      }
    }
  }

  public void resume() {
    final int gen;
    synchronized (lock) {
      if (!isPaused) return;
      isPaused = false;

      if (!isServerRunning()) {
        // Daemon died while backgrounded (e.g. low-memory kill). Relaunch it; default.pa
        // re-creates the sink. Wine clients may need to re-open, but this is the best we can do.
        start();
        return;
      }

      gen = ++reopenGeneration;
      // Force a full state transition (suspend then unsuspend) so module-aaudio-sink closes
      // and reopens its AAudio stream even if it was left in a broken state by the phone call.
      sendNativeCommands(
          buildSuspendSinkPacket(1, SINK_NAME, true),
          buildSuspendSinkPacket(2, SINK_NAME, false));
    }
    // Right after a call the audio route may not be released yet, so the first reopen can fail
    // silently. Re-issue the toggle a couple more times as insurance (each is a quick, mostly
    // inaudible close/reopen). The generation check cancels this if the state changes again.
    startReopenWatchdog(gen);
  }

  public boolean isServerRunning() {
    LocalSocket socket = new LocalSocket();
    try {
      socket.connect(
          new LocalSocketAddress(socketConfig.path, LocalSocketAddress.Namespace.FILESYSTEM));
      return true;
    } catch (IOException e) {
      return false;
    } finally {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    }
  }

  private void startReopenWatchdog(final int gen) {
    Thread thread =
        new Thread(
            () -> {
              int[] delaysMs = {600, 1400};
              for (int delay : delaysMs) {
                try {
                  Thread.sleep(delay);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
                synchronized (lock) {
                  if (gen != reopenGeneration || isPaused) return; // superseded or re-suspended
                  if (!isServerRunning()) return;
                  sendNativeCommands(
                      buildSuspendSinkPacket(1, SINK_NAME, true),
                      buildSuspendSinkPacket(2, SINK_NAME, false));
                }
              }
            },
            "PulseAudioReopen");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Opens one connection to the PulseAudio native-protocol socket, authenticates anonymously and
   * writes the given command packets in order. Returns false (and logs) if the server is
   * unreachable. Never throws.
   */
  private boolean sendNativeCommands(byte[]... commandPackets) {
    LocalSocket socket = new LocalSocket();
    try {
      socket.connect(
          new LocalSocketAddress(socketConfig.path, LocalSocketAddress.Namespace.FILESYSTEM));
      socket.setSoTimeout(150);
      OutputStream os = socket.getOutputStream();
      os.write(buildAuthPacket(0));
      for (byte[] packet : commandPackets) os.write(packet);
      os.flush();
      // Drain the replies briefly so the daemon has processed our commands before we disconnect.
      drain(socket.getInputStream());
      return true;
    } catch (IOException e) {
      Log.w(TAG, "PulseAudio native command failed: " + e.getMessage());
      return false;
    } finally {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    }
  }

  private static void drain(InputStream is) {
    byte[] buffer = new byte[512];
    int total = 0;
    try {
      // A couple of short reads is enough to confirm the handshake/command replies arrived.
      for (int i = 0; i < 4 && total < 8192; i++) {
        int n = is.read(buffer);
        if (n <= 0) break;
        total += n;
      }
    } catch (IOException ignored) {
      // SocketTimeoutException (subclass of IOException) is expected once the replies stop.
    }
  }

  private static byte[] buildAuthPacket(int tag) {
    ByteArrayOutputStream ts = new ByteArrayOutputStream();
    putU32(ts, PA_COMMAND_AUTH);
    putU32(ts, tag);
    putU32(ts, PA_PROTOCOL_VERSION); // no SHM/MEMFD flags set -> server disables shared memory for us
    putArbitrary(ts, new byte[PA_NATIVE_COOKIE_LENGTH]); // ignored by auth-anonymous=1
    return frameCommandPacket(ts.toByteArray());
  }

  private static byte[] buildSuspendSinkPacket(int tag, String sinkName, boolean suspend) {
    ByteArrayOutputStream ts = new ByteArrayOutputStream();
    putU32(ts, PA_COMMAND_SUSPEND_SINK);
    putU32(ts, tag);
    putU32(ts, PA_INVALID_INDEX); // select sink by name rather than index
    putString(ts, sinkName);
    putBoolean(ts, suspend);
    return frameCommandPacket(ts.toByteArray());
  }

  /** Wraps a tagstruct payload in the 20-byte pa_pstream control-packet descriptor. */
  private static byte[] frameCommandPacket(byte[] tagstruct) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    putRawU32(out, tagstruct.length); // LENGTH
    putRawU32(out, 0xFFFFFFFFL); // CHANNEL = (uint32) -1 marks a command packet
    putRawU32(out, 0); // OFFSET_HI
    putRawU32(out, 0); // OFFSET_LO
    putRawU32(out, 0); // FLAGS
    out.write(tagstruct, 0, tagstruct.length);
    return out.toByteArray();
  }

  private static void putRawU32(ByteArrayOutputStream b, long value) {
    b.write((int) ((value >> 24) & 0xFF));
    b.write((int) ((value >> 16) & 0xFF));
    b.write((int) ((value >> 8) & 0xFF));
    b.write((int) (value & 0xFF));
  }

  private static void putU32(ByteArrayOutputStream b, long value) {
    b.write(TAG_U32);
    putRawU32(b, value);
  }

  private static void putString(ByteArrayOutputStream b, String s) {
    if (s == null) {
      b.write(TAG_STRING_NULL);
      return;
    }
    b.write(TAG_STRING);
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    b.write(bytes, 0, bytes.length);
    b.write(0); // NUL terminator
  }

  private static void putArbitrary(ByteArrayOutputStream b, byte[] data) {
    b.write(TAG_ARBITRARY);
    putRawU32(b, data.length);
    b.write(data, 0, data.length);
  }

  private static void putBoolean(ByteArrayOutputStream b, boolean value) {
    b.write(value ? TAG_BOOLEAN_TRUE : TAG_BOOLEAN_FALSE);
  }

  private void killAllPulseAudioProcesses() {
    File proc = new File("/proc");
    String[] allPids = proc.list((dir, name) -> new File(dir, name).isDirectory() && name.matches("[0-9]+"));
    if (allPids == null) return;
    boolean killed = false;
    for (String pidStr : allPids) {
      String cmdline = readProcCmdline(pidStr);
      if (cmdline.contains("libpulseaudio.so")) {
        try {
          android.os.Process.killProcess(Integer.parseInt(pidStr));
          killed = true;
        } catch (NumberFormatException ignored) {
        }
      }
    }
    if (killed) {
      try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
  }

  private static String readProcCmdline(String pid) {
    try (FileInputStream fr = new FileInputStream("/proc/" + pid + "/cmdline")) {
      byte[] bytes = fr.readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8).replace('\0', ' ');
    } catch (IOException e) {
      return "";
    }
  }

  private void copyFromLibraryDir(File dst) {
    String[] libs =
        new String[] {
          "libltdl.so",
          "libpulseaudio.so",
          "libpulse.so",
          "libpulsecommon-13.0.so",
          "libpulsecore-13.0.so",
          "libsndfile.so"
        };
    for (int i = 0; i < libs.length; i++) {
      Path dstDir = Paths.get(dst.getAbsolutePath() + "/" + libs[i]);
      try (InputStream is =
          environment.getContext().getAssets().open("pulseaudio-bin/" + libs[i])) {
        if (is != null) {
          Files.copy(is, dstDir, StandardCopyOption.REPLACE_EXISTING);
          FileUtils.chmod(dstDir.toFile(), 0771);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private int execPulseAudio() {
    Context context = environment.getContext();
    File workingDir = new File(context.getFilesDir(), "/pulseaudio");
    if (!workingDir.isDirectory()) {
      workingDir.mkdirs();
      FileUtils.chmod(workingDir, 0771);
    }

    File configDir = new File(workingDir, ".config/pulse");
    if (!configDir.isDirectory()) configDir.mkdirs();
    File runtimeDir = new File(workingDir, "run");
    if (!runtimeDir.isDirectory()) runtimeDir.mkdirs();

    int sampleRate =
        options.sampleRateOverridden ? options.sampleRate : getNativeOutputSampleRate(context);
    int alternateSampleRate =
        options.alternateSampleRateOverridden
            ? options.alternateSampleRate
            : getAlternateSampleRate(sampleRate);
    String channelMap = getChannelMap(options.channels);

    File daemonConfigFile = new File(configDir, "daemon.conf");
    FileUtils.writeString(
        daemonConfigFile,
        String.join(
            "\n",
            "high-priority = yes",
            "realtime-scheduling = no",
            "flat-volumes = no",
            "enable-deferred-volume = no",
            "resample-method = speex-float-1",
            "avoid-resampling = yes",
            "default-sample-format = s16le",
            "default-sample-rate = " + sampleRate,
            "alternate-sample-rate = " + alternateSampleRate,
            "default-sample-channels = " + options.channels,
            "default-channel-map = " + channelMap,
            "default-fragments = 4",
            "default-fragment-size-msec = " + options.fragmentMillis,
            ""));

    File configFile = new File(workingDir, "default.pa");
    FileUtils.writeString(
        configFile,
        String.join(
            "\n",
            "load-module module-native-protocol-unix auth-anonymous=1 auth-cookie-enabled=0 socket=\""
                + socketConfig.path
                + "\"",
            "load-module module-aaudio-sink sink_name=AAudioSink rate=" + sampleRate,
            "set-default-sink AAudioSink",
            "set-sink-volume AAudioSink " + pulseVolumeHex(options.volume),
            ""));

    String archName = AppUtils.getArchName();
    File modulesDir = new File(workingDir, "modules/" + archName);
    patchAAudioSinkPerformanceMode(modulesDir);
    String systemLibPath = archName.equals("arm64") ? "/system/lib64" : "/system/lib";

    ArrayList<String> envVars = new ArrayList<>();
    envVars.add(
        "LD_LIBRARY_PATH=" + systemLibPath + ":" + modulesDir + ":" + workingDir.getAbsolutePath());
    envVars.add("HOME=" + workingDir);
    envVars.add("XDG_CONFIG_HOME=" + new File(workingDir, ".config").getAbsolutePath());
    envVars.add("PULSE_RUNTIME_PATH=" + runtimeDir.getAbsolutePath());
    envVars.add("PULSE_LATENCY_MSEC=" + options.latencyMillis);
    envVars.add("TMPDIR=" + environment.getTmpDir());

    copyFromLibraryDir(workingDir);

    String command = workingDir.getAbsolutePath() + "/libpulseaudio.so";
    command += " --system=false";
    command += " --disable-shm=true";
    command += " --fail=false";
    command += " -n --file=default.pa";
    command += " --daemonize=true";
    command += " --use-pid-file=false";
    command += " --exit-idle-time=-1";
    command += " --high-priority=true";
    command += " --realtime=false";
    command += " --resample-method=speex-float-1";

    // Note: with --daemonize=true the returned pid is the short-lived parent that forks the
    // daemon, so `pid` is not used to control it; lifecycle control goes through the native
    // protocol (suspend/resume) and killAllPulseAudioProcesses() (scan /proc by cmdline).
    return ProcessHelper.exec(command, envVars.toArray(new String[0]), workingDir);
  }

  private static String pulseVolumeHex(float linearVolume) {
    int pulseVolume = Math.max(0, Math.round(0x10000 * linearVolume));
    return "0x" + Integer.toHexString(pulseVolume);
  }

  private static int getNativeOutputSampleRate(Context context) {
    try {
      AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      String value = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
      if (value != null && !value.isEmpty()) return Math.max(8000, Integer.parseInt(value));
    } catch (Exception ignored) {
    }
    return Options.DEFAULT_SAMPLE_RATE;
  }

  private static int getAlternateSampleRate(int sampleRate) {
    return sampleRate == Options.DEFAULT_ALTERNATE_SAMPLE_RATE
        ? Options.DEFAULT_SAMPLE_RATE
        : Options.DEFAULT_ALTERNATE_SAMPLE_RATE;
  }

  private static String getChannelMap(int channels) {
    return channels <= 1 ? "mono" : "front-left,front-right";
  }

  private void patchAAudioSinkPerformanceMode(File modulesDir) {
    File module = new File(modulesDir, "module-aaudio-sink.so");
    if (!module.isFile()) return;

    int mode = 10;
    if (Options.PERFORMANCE_MODE_POWER_SAVING.equals(options.performanceMode)) mode = 11;
    else if (Options.PERFORMANCE_MODE_LOW_LATENCY.equals(options.performanceMode)) mode = 12;

    byte[][] searchPatterns = {
      {0x41, 0x01, (byte) 0x80, 0x52},
      {0x61, 0x01, (byte) 0x80, 0x52},
      {(byte) 0x81, 0x01, (byte) 0x80, 0x52},
      {0x0a, 0x10, (byte) 0xa0, (byte) 0xe3},
      {0x0b, 0x10, (byte) 0xa0, (byte) 0xe3},
      {0x0c, 0x10, (byte) 0xa0, (byte) 0xe3}
    };
    byte[] arm64Replacement = {(byte) (0x01 | (mode << 5)), 0x01, (byte) 0x80, 0x52};
    byte[] armhfReplacement = {(byte) mode, 0x10, (byte) 0xa0, (byte) 0xe3};

    try {
      byte[] data = Files.readAllBytes(module.toPath());
      if (data.length < 4
          || data[0] != 0x7F
          || data[1] != 'E'
          || data[2] != 'L'
          || data[3] != 'F') return;
      boolean changed = false;
      for (byte[] searchPattern : searchPatterns) {
        int offset = findPattern(data, searchPattern, 0);
        if (offset < 0) continue;
        if (findPattern(data, searchPattern, offset + 1) >= 0) continue;
        byte[] replacement = searchPattern[2] == (byte) 0x80 ? arm64Replacement : armhfReplacement;
        for (int j = 0; j < replacement.length; j++) {
          data[offset + j] = replacement[j];
        }
        changed = true;
        break;
      }
      if (changed) Files.write(module.toPath(), data);
    } catch (IOException ignored) {
    }
  }

  private static int findPattern(byte[] data, byte[] pattern, int fromIndex) {
    for (int i = Math.max(0, fromIndex); i <= data.length - pattern.length; i++) {
      boolean found = true;
      for (int j = 0; j < pattern.length; j++) {
        if (data[i + j] != pattern[j]) {
          found = false;
          break;
        }
      }
      if (found) return i;
    }
    return -1;
  }
}
