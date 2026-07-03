package com.winlator.cmod.runtime.display.environment;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.util.Log;

/**
 * Complements the activity lifecycle for suspending/resuming guest audio around interruptions that
 * do not cleanly pause the activity — most importantly incoming phone calls and VoIP calls.
 *
 * <p>The activity's onPause/onResume already suspend/resume audio, but a phone call arrives as a
 * transient audio-focus loss and, when it ends, we get a precise focus-gain callback which is a more
 * reliable "the call is over, restore audio now" signal than waiting for the activity to be brought
 * back to the foreground. The suspend/resume callbacks here are idempotent with the lifecycle ones,
 * so firing from both paths is safe.
 *
 * <p>Focus callbacks are delivered on the main thread; the suspend/resume work touches unix sockets,
 * so it is dispatched to a background thread to keep the UI responsive.
 */
public class AudioFocusHandler {
  private static final String TAG = "AudioFocusHandler";

  private final AudioManager audioManager;
  private final Runnable onSuspend;
  private final Runnable onResume;
  private final AudioFocusRequest focusRequest;

  private boolean requested = false;
  private boolean suspendedByFocusLoss = false;

  public AudioFocusHandler(Context context, Runnable onSuspend, Runnable onResume) {
    this.audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    this.onSuspend = onSuspend;
    this.onResume = onResume;

    AudioAttributes attributes =
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    this.focusRequest =
        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(this::onFocusChange)
            .build();
  }

  /** Requests audio focus so we start receiving focus-change callbacks. Safe to call repeatedly. */
  public synchronized void request() {
    if (audioManager == null || requested) return;
    try {
      int result = audioManager.requestAudioFocus(focusRequest);
      requested = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    } catch (Exception e) {
      Log.w(TAG, "requestAudioFocus failed: " + e.getMessage());
    }
  }

  /** Abandons audio focus and stops callbacks. Call on teardown. */
  public synchronized void release() {
    if (audioManager == null || !requested) return;
    try {
      audioManager.abandonAudioFocusRequest(focusRequest);
    } catch (Exception e) {
      Log.w(TAG, "abandonAudioFocus failed: " + e.getMessage());
    }
    requested = false;
    suspendedByFocusLoss = false;
  }

  private void onFocusChange(int focusChange) {
    switch (focusChange) {
      case AudioManager.AUDIOFOCUS_LOSS:
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
        // Phone call / another app took over playback. Release the AAudio device.
        synchronized (this) {
          suspendedByFocusLoss = true;
        }
        dispatch(onSuspend);
        break;
      case AudioManager.AUDIOFOCUS_GAIN:
        // Interruption ended; restore output. Only if we were the ones who suspended, so we
        // don't fight a resume the activity lifecycle is already driving.
        boolean shouldResume;
        synchronized (this) {
          shouldResume = suspendedByFocusLoss;
          suspendedByFocusLoss = false;
        }
        if (shouldResume) dispatch(onResume);
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
      default:
        // Ducking / unknown: leave playback alone.
        break;
    }
  }

  private static void dispatch(Runnable task) {
    if (task == null) return;
    Thread thread = new Thread(task, "AudioFocusDispatch");
    thread.setDaemon(true);
    thread.start();
  }
}
