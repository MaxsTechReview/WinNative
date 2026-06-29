package com.winlator.cmod.runtime.display.recording;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Records the game's composited output to an MP4 (H.264 video + AAC audio). Video frames come from
 * the native Vulkan renderer (mirror-presented into {@link #getInputSurface()}); game audio is teed
 * in via {@link #onPcm}. A background thread drains both encoders into the muxer.
 */
public final class GameRecorder {
    private static final String TAG = "GameRecorder";

    private static final String VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int AUDIO_BITRATE = 128_000;
    private static final long DEQUEUE_TIMEOUT_US = 10_000;

    // The active recorder the audio bridge tees PCM into (one recording session at a time).
    private static volatile GameRecorder activeRecorder;

    public static GameRecorder active() {
        return activeRecorder;
    }

    private final Context appContext;

    // Video
    private MediaCodec videoCodec;
    private Surface inputSurface;
    private int videoTrackIndex = -1;

    // Audio (lazily configured from the first PCM buffer so we match the game's real format)
    private MediaCodec audioCodec;
    private int audioTrackIndex = -1;
    private int audioSampleRate;
    private int audioChannelCount;
    private long audioFramesSubmitted; // advances the audio sample clock
    private long audioBasePtsUs = -1;  // wall-clock anchor of the first PCM, shared with video base

    private MediaMuxer muxer;
    private boolean muxerStarted;
    private File outputFile;
    private int orientationHint; // clockwise degrees players rotate playback to upright

    private Thread drainThread;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private long baseTimeNs;
    // If no game audio has appeared by this deadline, start the muxer video-only rather than stall.
    private long audioGraceDeadlineNs;
    private static final long AUDIO_GRACE_NS = 1_000_000_000L;

    // Samples that arrive before both tracks are added + muxer is started.
    private final List<PendingSample> pending = new ArrayList<>();

    private static final class PendingSample {
        final boolean video;
        final ByteBuffer data;
        final MediaCodec.BufferInfo info;

        PendingSample(boolean video, ByteBuffer data, MediaCodec.BufferInfo info) {
            this.video = video;
            this.data = data;
            this.info = info;
        }
    }

    public GameRecorder(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean isRecording() {
        return recording.get();
    }

    /** Configure the encoder + muxer and start draining. Returns the input Surface, or null on failure. */
    public synchronized Surface start(int width, int height, int fps, int orientationHint, int bitRate) {
        if (recording.get()) return inputSurface;
        width &= ~1; // encoders want even dimensions
        height &= ~1;
        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Refusing to record invalid size " + width + "x" + height);
            return null;
        }
        if (fps <= 0 || fps > 240) fps = 60;
        if (bitRate <= 0) bitRate = estimateVideoBitrate(width, height, fps);
        this.orientationHint = ((orientationHint % 360) + 360) % 360;

        try {
            if (!openOutput()) {
                abortOutput();
                return null;
            }

            MediaFormat fmt = MediaFormat.createVideoFormat(VIDEO_MIME, width, height);
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                fmt.setInteger(MediaFormat.KEY_BITRATE_MODE,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            }

            videoCodec = MediaCodec.createEncoderByType(VIDEO_MIME);
            videoCodec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = videoCodec.createInputSurface();
            videoCodec.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start video encoder", e);
            releaseQuietly();
            abortOutput();
            return null;
        }

        baseTimeNs = System.nanoTime();
        audioGraceDeadlineNs = baseTimeNs + AUDIO_GRACE_NS;
        stopRequested.set(false);
        recording.set(true);
        activeRecorder = this;

        drainThread = new Thread(this::drainLoop, "GameRecorderDrain");
        drainThread.start();
        Log.i(TAG, "Recording started " + width + "x" + height + "@" + fps);
        return inputSurface;
    }

    public Surface getInputSurface() {
        return inputSurface;
    }

    /** Tee a buffer of already-played game PCM into the recording (non-blocking; does not consume it). */
    public void onPcm(ByteBuffer data, int sampleRate, int channelCount, int pcmEncoding) {
        if (!recording.get() || stopRequested.get()) return;
        try {
            ensureAudioEncoder(sampleRate, channelCount);
        } catch (Exception e) {
            Log.e(TAG, "Audio encoder init failed; continuing video-only", e);
            audioCodec = null; // give up on audio, keep recording video
            return;
        }
        MediaCodec codec = audioCodec;
        if (codec == null) return;

        ByteBuffer pcm16 = toPcm16(data, pcmEncoding);
        if (pcm16 == null || pcm16.remaining() == 0) return;

        // Anchor the audio clock to the same base as the video Surface timestamps, then advance by samples.
        if (audioBasePtsUs < 0) {
            audioBasePtsUs = Math.max(0L, (System.nanoTime() - baseTimeNs) / 1000L);
        }
        try {
            while (pcm16.hasRemaining()) {
                int inIndex = codec.dequeueInputBuffer(0);
                if (inIndex < 0) break; // no input buffer free right now — drop the rest this tick
                ByteBuffer in = codec.getInputBuffer(inIndex);
                if (in == null) break;
                in.clear();
                int chunk = Math.min(in.remaining(), pcm16.remaining());
                int oldLimit = pcm16.limit();
                pcm16.limit(pcm16.position() + chunk);
                in.put(pcm16);
                pcm16.limit(oldLimit);

                long ptsUs = audioBasePtsUs
                        + audioFramesSubmitted * 1_000_000L / Math.max(1, audioSampleRate);
                int bytesPerFrame = audioChannelCount * 2;
                audioFramesSubmitted += chunk / Math.max(1, bytesPerFrame);
                codec.queueInputBuffer(inIndex, 0, chunk, ptsUs, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "queue audio failed", e);
        }
    }

    public synchronized void stop() {
        if (!recording.get()) return;
        stopRequested.set(true);
        try {
            if (videoCodec != null) videoCodec.signalEndOfInputStream();
        } catch (Exception ignore) {}
        if (audioCodec != null) {
            try {
                int inIndex = audioCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                if (inIndex >= 0) {
                    long base = audioBasePtsUs < 0 ? 0L : audioBasePtsUs;
                    long ptsUs = base
                            + audioFramesSubmitted * 1_000_000L / Math.max(1, audioSampleRate);
                    audioCodec.queueInputBuffer(inIndex, 0, 0, ptsUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            } catch (Exception ignore) {}
        }

        // Wait for the drain thread to exit before finishOutput() releases the muxer.
        Thread t = drainThread;
        if (t != null) {
            try {
                t.join(4_000);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
        }
        recording.set(false);
        if (activeRecorder == this) activeRecorder = null;
        finishOutput();
        releaseQuietly();
        Log.i(TAG, "Recording stopped");
    }

    // ── Draining ─────────────────────────────────────────────────────────────

    private void drainLoop() {
        MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
        boolean videoDone = false;
        boolean audioDone = false;

        long stopDeadlineNs = 0;
        while (!videoDone) {
            videoDone = drainEncoder(videoCodec, videoInfo, true);
            if (audioCodec != null && !audioDone) { // appears partway through (lazy init)
                audioDone = drainEncoder(audioCodec, audioInfo, false);
            }
            synchronized (this) { maybeStartMuxer(); }
            if (stopRequested.get()) {
                // Bound the wait for EOS so stop() can finalize without nulling the muxer mid-drain.
                if (stopDeadlineNs == 0) stopDeadlineNs = System.nanoTime() + 1_500_000_000L;
                else if (System.nanoTime() > stopDeadlineNs) break;
            } else if (!videoDone) {
                try { Thread.sleep(2); } catch (InterruptedException e) { break; }
            }
        }
        // After video EOS, flush any remaining audio so trailing sound isn't truncated.
        if (audioCodec != null) {
            long deadline = System.nanoTime() + 500_000_000L;
            while (!audioDone && System.nanoTime() < deadline) {
                audioDone = drainEncoder(audioCodec, audioInfo, false);
            }
        }
    }

    /** Returns true when this encoder has emitted end-of-stream. */
    private boolean drainEncoder(MediaCodec codec, MediaCodec.BufferInfo info, boolean video) {
        if (codec == null) return true;
        try {
            int index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return false;
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = codec.getOutputFormat();
                synchronized (this) {
                    // Tracks can only be added before the muxer starts; a late stream is dropped.
                    if (!muxerStarted && muxer != null) {
                        if (video) videoTrackIndex = muxer.addTrack(newFormat);
                        else audioTrackIndex = muxer.addTrack(newFormat);
                        maybeStartMuxer();
                    }
                }
                return false;
            } else if (index >= 0) {
                ByteBuffer out = codec.getOutputBuffer(index);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0; // config already consumed by addTrack
                }
                if (video && info.size > 0) {
                    // Rebase Surface timestamps to the recording start (shared origin with audio).
                    info.presentationTimeUs = Math.max(0L,
                            info.presentationTimeUs - baseTimeNs / 1000L);
                }
                if (out != null && info.size > 0) {
                    out.position(info.offset);
                    out.limit(info.offset + info.size);
                    writeSample(video, out, info);
                }
                boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(index, false);
                return eos;
            }
        } catch (IllegalStateException e) {
            // Codec released underneath us during stop().
            return true;
        }
        return false;
    }

    private synchronized void writeSample(boolean video, ByteBuffer data,
                                          MediaCodec.BufferInfo info) {
        int track = video ? videoTrackIndex : audioTrackIndex;
        if (muxerStarted) {
            if (track < 0) return; // stream not in the muxer (e.g. late audio) — drop, don't buffer
            try {
                muxer.writeSampleData(track, data, info);
            } catch (Exception e) {
                Log.e(TAG, "writeSampleData failed", e);
            }
            return;
        }
        // Muxer not started yet: hold a copy until both tracks are added.
        ByteBuffer copy = ByteBuffer.allocate(info.size);
        copy.put(data);
        copy.flip();
        MediaCodec.BufferInfo ci = new MediaCodec.BufferInfo();
        ci.set(0, info.size, info.presentationTimeUs, info.flags);
        pending.add(new PendingSample(video, copy, ci));
    }

    /** Start the muxer once the video track is known; include audio if it has appeared. */
    private void maybeStartMuxer() {
        if (muxerStarted || muxer == null) return;
        if (videoTrackIndex < 0) return;
        // If audio exists, wait for its track; if none has appeared, give it a grace window first.
        boolean audioTrackPending = audioCodec != null && audioTrackIndex < 0;
        boolean audioMightStillAppear = audioCodec == null && System.nanoTime() < audioGraceDeadlineNs;
        if (audioTrackPending || audioMightStillAppear) return;
        try {
            muxer.start();
            muxerStarted = true;
            for (PendingSample s : pending) {
                int track = s.video ? videoTrackIndex : audioTrackIndex;
                if (track >= 0) muxer.writeSampleData(track, s.data, s.info);
            }
            pending.clear();
        } catch (Exception e) {
            Log.e(TAG, "muxer.start failed", e);
        }
    }

    // ── Audio helpers ────────────────────────────────────────────────────────

    private synchronized void ensureAudioEncoder(int sampleRate, int channelCount) throws Exception {
        if (audioCodec != null || sampleRate <= 0 || channelCount <= 0) return;
        channelCount = Math.min(channelCount, 2); // AAC-LC: encode stereo at most
        MediaFormat fmt = MediaFormat.createAudioFormat(AUDIO_MIME, sampleRate, channelCount);
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE);
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024);
        MediaCodec codec = MediaCodec.createEncoderByType(AUDIO_MIME);
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        audioSampleRate = sampleRate;
        audioChannelCount = channelCount;
        audioCodec = codec;
    }

    /** Convert source PCM to interleaved 16-bit little-endian (the AAC encoder's input format). */
    private static ByteBuffer toPcm16(ByteBuffer src, int pcmEncoding) {
        int pos = src.position();
        try {
            if (pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
                if (src.order() == ByteOrder.LITTLE_ENDIAN) {
                    ByteBuffer out = ByteBuffer.allocate(src.remaining());
                    out.put(src.duplicate());
                    out.flip();
                    return out.order(ByteOrder.LITTLE_ENDIAN);
                }
                // Big-endian source: read shorts in source order, write little-endian.
                ByteBuffer s = src.duplicate();
                s.order(src.order());
                int shorts = s.remaining() / 2;
                ByteBuffer out = ByteBuffer.allocate(shorts * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < shorts; i++) out.putShort(s.getShort());
                out.flip();
                return out;
            } else if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                ByteBuffer s = src.duplicate();
                s.order(src.order());
                int floats = s.remaining() / 4;
                ByteBuffer out = ByteBuffer.allocate(floats * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < floats; i++) {
                    float f = s.getFloat();
                    int v = Math.round(f * 32767f);
                    if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
                    out.putShort((short) v);
                }
                out.flip();
                return out;
            } else if (pcmEncoding == AudioFormat.ENCODING_PCM_8BIT) {
                ByteBuffer s = src.duplicate();
                int n = s.remaining();
                ByteBuffer out = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < n; i++) {
                    int u = s.get() & 0xFF;       // unsigned 8-bit
                    out.putShort((short) ((u - 128) << 8));
                }
                out.flip();
                return out;
            }
            return null;
        } finally {
            src.position(pos);
        }
    }

    private static int estimateVideoBitrate(int width, int height, int fps) {
        // ~0.07 bits per pixel·frame, clamped to a sane window for shareable clips.
        long bps = (long) (width * (long) height * fps * 0.07);
        return (int) Math.max(4_000_000L, Math.min(bps, 50_000_000L));
    }

    // ── Output (WinNative/Recordings in the app's external files dir) ────────

    /** /sdcard/WinNative/Recordings (alongside logs/profiles/saves; needs MANAGE_EXTERNAL_STORAGE). */
    private File recordingsDir() {
        File ext = Environment.getExternalStorageDirectory();
        if (ext != null) return new File(ext, "WinNative/Recordings");
        File base = appContext.getExternalFilesDir(null);
        if (base == null) base = appContext.getFilesDir();
        return new File(base, "Recordings");
    }

    private boolean openOutput() {
        String name = "WinNative_" + System.currentTimeMillis() + ".mp4";
        try {
            File dir = recordingsDir();
            if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
                Log.e(TAG, "Could not create " + dir);
                return false;
            }
            outputFile = new File(dir, name);
            muxer = new MediaMuxer(outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            // Must be set before the muxer starts; rotates playback to upright on any player.
            if (orientationHint != 0) muxer.setOrientationHint(orientationHint);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "openOutput failed", e);
            return false;
        }
    }

    private void finishOutput() {
        if (muxer != null) {
            try {
                if (muxerStarted) muxer.stop();
            } catch (Exception ignore) {}
            try {
                muxer.release();
            } catch (Exception ignore) {}
            muxer = null;
        }
        muxerStarted = false;
        // Make the new file visible to media scanners / file managers right away.
        if (outputFile != null && outputFile.length() > 0) {
            try {
                MediaScannerConnection.scanFile(appContext,
                        new String[]{outputFile.getAbsolutePath()}, new String[]{"video/mp4"}, null);
            } catch (Exception ignore) {}
        }
        outputFile = null;
    }

    /** Release output without finalizing, and delete the empty file — used when start() fails. */
    private void abortOutput() {
        if (muxer != null) {
            try { muxer.release(); } catch (Exception ignore) {}
            muxer = null;
        }
        muxerStarted = false;
        try {
            if (outputFile != null) //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
        } catch (Exception ignore) {}
        outputFile = null;
    }

    private void releaseQuietly() {
        try {
            if (videoCodec != null) videoCodec.stop();
        } catch (Exception ignore) {}
        try {
            if (videoCodec != null) videoCodec.release();
        } catch (Exception ignore) {}
        videoCodec = null;
        try {
            if (inputSurface != null) inputSurface.release();
        } catch (Exception ignore) {}
        inputSurface = null;
        try {
            if (audioCodec != null) audioCodec.stop();
        } catch (Exception ignore) {}
        try {
            if (audioCodec != null) audioCodec.release();
        } catch (Exception ignore) {}
        audioCodec = null;
        drainThread = null;
        videoTrackIndex = -1;
        audioTrackIndex = -1;
        audioFramesSubmitted = 0;
        audioBasePtsUs = -1;
    }
}
