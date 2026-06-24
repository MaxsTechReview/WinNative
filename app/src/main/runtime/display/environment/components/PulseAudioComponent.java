package com.winlator.cmod.runtime.display.environment.components;

import android.content.Context;
import android.media.AudioManager;
import android.os.Process;
import com.winlator.cmod.runtime.display.connector.UnixSocketConfig;
import com.winlator.cmod.runtime.display.environment.EnvironmentComponent;
import com.winlator.cmod.runtime.system.ProcessHelper;
import com.winlator.cmod.runtime.wine.EnvVars;
import com.winlator.cmod.shared.android.AppUtils;
import com.winlator.cmod.shared.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

public class PulseAudioComponent extends EnvironmentComponent {
  private final UnixSocketConfig socketConfig;
  private final Options options;
  private static int pid = -1;
  private static final Object lock = new Object();

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
      stop();
      pid = execPulseAudio();
    }
  }

  @Override
  public void stop() {
    synchronized (lock) {
      if (pid != -1) {
        Process.killProcess(pid);
        pid = -1;
      }
    }
  }

  public void suspend() {
    synchronized (lock) {
      if (pid != -1) ProcessHelper.suspendProcess(pid);
    }
  }

  public void resume() {
    synchronized (lock) {
      if (pid != -1) ProcessHelper.resumeProcess(pid);
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
    command += " --daemonize=false";
    command += " --use-pid-file=false";
    command += " --exit-idle-time=-1";
    command += " --high-priority=true";
    command += " --realtime=false";
    command += " --resample-method=speex-float-1";

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
