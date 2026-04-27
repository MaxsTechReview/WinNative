package com.winlator.cmod.runtime.audio.alsaserver;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import com.winlator.cmod.runtime.wine.EnvVars;
import com.winlator.cmod.sharedmemory.SysVSharedMemory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ALSAClient {
  public enum DataType {
    U8(1),
    S16LE(2),
    S16BE(2),
    FLOATLE(4),
    FLOATBE(4);
    public final byte byteCount;

    DataType(int byteCount) {
      this.byteCount = (byte) byteCount;
    }
  }

  private DataType dataType = DataType.U8;
  private byte channelCount = 2;
  private int sampleRate = 0;
  private int positionFrames;
  private int bufferSize;
  private int frameBytes;
  private ByteBuffer sharedBuffer;
  private ByteBuffer auxBuffer;
  private AudioTrack audioTrack;
  private int bufferCapacityFrames;
  private int previousUnderrunCount = 0;
  private static short framesPerBuffer = 256;
  private final Options options;

  public static class Options {
    public static final int DEFAULT_LATENCY_MILLIS = 16;
    public static final float DEFAULT_VOLUME = 1.0f;

    public int latencyMillis = DEFAULT_LATENCY_MILLIS;
    public int performanceMode = AudioTrack.PERFORMANCE_MODE_LOW_LATENCY;
    public float volume = DEFAULT_VOLUME;

    public static Options fromEnvVars(EnvVars envVars) {
      Options options = new Options();
      if (envVars == null) return options;

      options.latencyMillis =
          parseInt(
              firstNonEmpty(envVars.get("ANDROID_ALSA_LATENCY_MS"), envVars.get("WINNATIVE_ALSA_LATENCY_MS")),
              DEFAULT_LATENCY_MILLIS);
      options.latencyMillis = Math.max(0, options.latencyMillis);

      options.volume =
          parseFloat(
              firstNonEmpty(envVars.get("ANDROID_ALSA_VOLUME"), envVars.get("WINNATIVE_ALSA_VOLUME")),
              DEFAULT_VOLUME);
      options.volume = Math.max(0.0f, Math.min(options.volume, 1.0f));

      String performanceMode =
          firstNonEmpty(
              envVars.get("ANDROID_ALSA_PERFORMANCE_MODE"), envVars.get("WINNATIVE_ALSA_PERFORMANCE_MODE"));
      if (performanceMode.equalsIgnoreCase("none") || performanceMode.equals("0")) {
        options.performanceMode = AudioTrack.PERFORMANCE_MODE_NONE;
      } else if (performanceMode.equalsIgnoreCase("power_saving") || performanceMode.equals("2")) {
        options.performanceMode = AudioTrack.PERFORMANCE_MODE_POWER_SAVING;
      } else {
        options.performanceMode = AudioTrack.PERFORMANCE_MODE_LOW_LATENCY;
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

  public ALSAClient() {
    this(new Options());
  }

  public ALSAClient(Options options) {
    this.options = options != null ? options : new Options();
  }

  public synchronized void release() {
    if (sharedBuffer != null) {
      SysVSharedMemory.unmapSHMSegment(sharedBuffer, sharedBuffer.capacity());
      sharedBuffer = null;
    }
    auxBuffer = null;

    AudioTrack track = audioTrack;
    audioTrack = null;
    if (track != null) {
      try {
        track.pause();
      } catch (Exception ignored) {
      }
      try {
        track.flush();
      } catch (Exception ignored) {
      }
      try {
        track.release();
      } catch (Exception ignored) {
      }
    }
  }

  public synchronized void prepare() {
    positionFrames = 0;
    previousUnderrunCount = 0;
    frameBytes = channelCount * dataType.byteCount;
    release();

    if (!isValidBufferSize()) return;

    AudioFormat format =
        new AudioFormat.Builder()
            .setEncoding(getPCMEncoding(dataType))
            .setSampleRate(sampleRate)
            .setChannelMask(getChannelConfig(channelCount))
            .build();

    try {
      int audioTrackBufferSize = getAudioTrackBufferSizeInBytes();
      audioTrack =
          new AudioTrack.Builder()
              .setPerformanceMode(options.performanceMode)
              .setAudioFormat(format)
              .setBufferSizeInBytes(audioTrackBufferSize)
              .build();
      bufferCapacityFrames = audioTrack.getBufferCapacityInFrames();
      if (options.volume != Options.DEFAULT_VOLUME) audioTrack.setVolume(options.volume);
      audioTrack.play();
    } catch (Exception e) {
      release();
    }
  }

  public synchronized void start() {
    if (audioTrack != null && audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
      try {
        audioTrack.play();
      } catch (Exception ignored) {
      }
    }
  }

  public synchronized void stop() {
    if (audioTrack != null) {
      try {
        audioTrack.stop();
      } catch (Exception ignored) {
      }
      try {
        audioTrack.flush();
      } catch (Exception ignored) {
      }
    }
  }

  public synchronized void pause() {
    if (audioTrack != null) {
      try {
        audioTrack.pause();
      } catch (Exception ignored) {
      }
    }
  }

  public synchronized void drain() {
    if (audioTrack != null) {
      try {
        audioTrack.flush();
      } catch (Exception ignored) {
      }
    }
  }

  public synchronized void writeDataToStream(ByteBuffer data) {
    if (dataType == DataType.S16LE || dataType == DataType.FLOATLE) {
      data.order(ByteOrder.LITTLE_ENDIAN);
    } else if (dataType == DataType.S16BE || dataType == DataType.FLOATBE) {
      data.order(ByteOrder.BIG_ENDIAN);
    }

    if (audioTrack != null) {
      data.position(0);

      while (data.position() != data.limit()) {
        int bytesWritten;
        try {
          bytesWritten = audioTrack.write(data, data.remaining(), AudioTrack.WRITE_BLOCKING);
        } catch (Exception e) {
          break;
        }
        if (bytesWritten <= 0) break;

        positionFrames += bytesWritten / frameBytes;
        increaseBufferSizeIfUnderrunOccurs();
      }
      data.rewind();
    }
  }

  public int pointer() {
    return positionFrames;
  }

  public void setDataType(DataType dataType) {
    this.dataType = dataType;
  }

  public void setChannelCount(int channelCount) {
    this.channelCount = (byte) channelCount;
  }

  public void setSampleRate(int sampleRate) {
    this.sampleRate = sampleRate;
  }

  public void setBufferSize(int bufferSize) {
    this.bufferSize = bufferSize;
  }

  public ByteBuffer getSharedBuffer() {
    return sharedBuffer;
  }

  public void setSharedBuffer(ByteBuffer sharedBuffer) {
    if (sharedBuffer != null) {
      auxBuffer = ByteBuffer.allocateDirect(getBufferSizeInBytes()).order(ByteOrder.LITTLE_ENDIAN);
      this.sharedBuffer = sharedBuffer.order(ByteOrder.LITTLE_ENDIAN);
    } else {
      auxBuffer = null;
      this.sharedBuffer = null;
    }
  }

  public ByteBuffer getAuxBuffer() {
    return auxBuffer;
  }

  public DataType getDataType() {
    return dataType;
  }

  public byte getChannelCount() {
    return channelCount;
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public int getBufferSize() {
    return bufferSize;
  }

  public int getBufferSizeInBytes() {
    return bufferSize * frameBytes;
  }

  private boolean isValidBufferSize() {
    return (getBufferSizeInBytes() % frameBytes == 0) && bufferSize > 0;
  }

  public int computeLatencyMillis() {
    return (int) (((float) bufferSize / sampleRate) * 1000);
  }

  private int getAudioTrackBufferSizeInBytes() {
    if (options.latencyMillis <= 0 || sampleRate <= 0) return getBufferSizeInBytes();
    int latencyFrames = (int) Math.ceil((options.latencyMillis * sampleRate) / 1000.0);
    latencyFrames = roundUpToFramesPerBuffer(latencyFrames);
    return Math.max(bufferSize, latencyFrames) * frameBytes;
  }

  private static int roundUpToFramesPerBuffer(int frames) {
    int quantum = Math.max(1, framesPerBuffer);
    return ((frames + quantum - 1) / quantum) * quantum;
  }

  private void increaseBufferSizeIfUnderrunOccurs() {
    if (audioTrack == null) return;
    int underrunCount = audioTrack.getUnderrunCount();
    if (underrunCount > previousUnderrunCount && bufferSize < bufferCapacityFrames) {
      previousUnderrunCount = underrunCount;
      bufferSize = Math.min(bufferSize + framesPerBuffer, bufferCapacityFrames);
      audioTrack.setBufferSizeInFrames(bufferSize);
    }
  }

  private static int getPCMEncoding(DataType dataType) {
    switch (dataType) {
      case U8:
        return AudioFormat.ENCODING_PCM_8BIT;
      case FLOATLE:
      case FLOATBE:
        return AudioFormat.ENCODING_PCM_FLOAT;
      case S16LE:
      case S16BE:
      default:
        return AudioFormat.ENCODING_PCM_16BIT;
    }
  }

  private static int getChannelConfig(int channelCount) {
    return channelCount <= 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
  }

  public static void assignFramesPerBuffer(Context context) {
    try {
      AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      String value = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
      framesPerBuffer = Short.parseShort(value);
      if (framesPerBuffer == 0) framesPerBuffer = 256;
    } catch (Exception e) {
      framesPerBuffer = 256;
    }
  }
}
