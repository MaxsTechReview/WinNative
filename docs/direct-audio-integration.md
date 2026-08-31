# DirectAudio integration

**DirectAudio** is a native Wine → Android AAudio mmdevapi driver by
[The412Banner](https://github.com/The412Banner/directaudio), licensed LGPL-2.1-or-later.
WinNative ships his release binaries unmodified and adds only the host-side plumbing. This
document covers what the driver does, why it is the only audio path here that can carry a
microphone, and what the app does to select and configure it.

> DirectAudio by The412Banner (https://github.com/The412Banner/directaudio)

Bundled artifacts, checksums and the LGPL source offer: [`app/src/main/assets/directaudio/PROVENANCE.md`](../app/src/main/assets/directaudio/PROVENANCE.md).

---

## Why the other two stacks cannot do microphone input

This is structural, not missing configuration.

**ALSA (custom aserver).** The wire protocol between the guest ALSA plugin and the app's Java
server has eight request codes — `CLOSE START STOP PAUSE PREPARE WRITE DRAIN POINTER`
(`runtime/audio/alsaserver/RequestCodes.java`) — and every one is output-side. There is no
`READ`. The guest plugin does not even reach the socket for a capture PCM:

```c
/* audio_plugin/module_pcm_android_aserver.c:317 */
if (stream != SND_PCM_STREAM_PLAYBACK) return -ENOTSUP;
```

Adding capture here means designing a new verb on both sides of the socket plus an
`AudioRecord` feeder in `ALSAClient`. That is a new subsystem.

**PulseAudio.** The bundled `pulseaudio.tzst` asset contains exactly `libprotocol-native.so`,
`module-aaudio-sink.so`, `module-native-protocol-unix.so` and `pactl`, and
`PulseAudioComponent` loads only the protocol module and the sink. There is no source module of
any kind, so a capture endpoint winepulse advertises records silence or a monitor tap rather
than the microphone.

**DirectAudio.** There is no daemon in the path at all — the driver talks AAudio directly from
Wine's unixlib, so it is both the mmdevapi backend and the device layer. The capture half of
the WASAPI vtable was always wired; v1.3.2 exposes the endpoint and backs it with a real AAudio
input stream.

---

## How the driver's capture path works

It is the render mixer, inverted: one shared AAudio `INPUT` stream whose data callback fans PCM
out to every registered capture voice, reusing the render ring fields with the producer and
consumer roles swapped (the same approach Wine's own `winecoreaudio.drv` uses). That sharing is
why `create_stream`, the per-period stream event and the vtable did not need duplicating.

| Stage | What happens |
| --- | --- |
| Gate | `BANNER_AUDIO_DIRECT_MIC` is read **once** at process attach. Default off. |
| Enumerate | With the gate off, `get_endpoint_ids` reports **zero** capture endpoints. |
| Open | First capture `create_stream` lazily opens the AAudio input — 48 kHz / float / stereo, `VOICE_COMMUNICATION` preset (platform AEC / NS / AGC). Enumeration alone never gets here. |
| Start | `requestStart` is deferred until the first `Start`, so the mic goes hot no earlier than the game actually records. |
| Produce | The callback rate-converts with a carried fractional position, folds channels, writes the client sample format, and drops the oldest frame on overrun. |
| Consume | `get_capture_buffer` serves device-period chunks, returning `AUDCLNT_S_BUFFER_EMPTY` until a full period is held. |
| Teardown | Last voice out → `requestStop` (the OS recording indicator clears), stream stays open for reuse. |

The driver honours the geometry AAudio actually grants rather than what it asked for, so a mono
microphone or a device-native rate still works. Capture gain is deliberately **not** scaled by
the stream volumes — the `VOICE_COMMUNICATION` auto-gain owns the mic level and a WASAPI capture
"volume" on top of it would fight the platform AGC.

### Why the gate is off by default

A capture endpoint a game can enumerate but **not** open makes titles that probe the microphone
during load abandon audio initialisation and boot to a black screen. God of War and DiRT 3 are
the upstream device-proven examples. With `BANNER_AUDIO_DIRECT_MIC` unset the driver is
byte-identical to its render-only build: no capture endpoints, every capture op returns
`AUDCLNT_E_DEVICE_INVALIDATED`, and no AAudio input object is ever constructed.

---

## What WinNative does

All host-side logic lives in
[`runtime/audio/directaudio/DirectAudioDriver.kt`](../app/src/main/runtime/audio/directaudio/DirectAudioDriver.kt).

### Driver selection and overlay

Two axes decide which bundled build is installed:

- **Wine ABI** — one Wine-11 build serves Proton 11.0-1 / -3 / -5 / -6 (ABI-interchangeable);
  Proton 10.0-4 needs the separate wine10 build. Anything else is reported unsupported rather
  than guessed at.
- **Kernel page size** — `sdk35` for a 16 KB-page kernel, `sdk28` for the classic 4 KB, read via
  `Os.sysconf(_SC_PAGESIZE)`. Loading the wrong one fails to map. Nothing else in the app needed
  page size before, so the check is new.

The complete 3-file set is then overlaid onto the container's Wine layer, stamped by bundled
version + variant so it is a no-op on every launch after the first:

```
lib/wine/aarch64-windows/winedirectaudio.drv   arm64ec PE  — 64-bit guest games
lib/wine/i386-windows/winedirectaudio.drv      32-bit PE   — wow64 guest games
lib/wine/aarch64-unix/winedirectaudio.so       bionic unixlib — the AAudio backend
```

Which PE the loader picks is decided by the **guest game's** bitness, not the device, so both PE
halves are always installed alongside the shared unixlib.

### Driver activation

`changeWineAudioDriver()` in `XServerDisplayActivity` writes the Wine registry key that selects
the backend — `Software\Wine\Drivers` → `Audio` = `directaudio`. Selecting DirectAudio adds no
environment component and opens no socket, because there is no daemon to start.

### Microphone opt-in

The toggle is per container and per shortcut (extra `directAudioMic`), surfaced in the audio
settings only when DirectAudio is the selected driver, and defaulted off. Turning it on requests
`RECORD_AUDIO` immediately rather than at app start.

At launch, `BANNER_AUDIO_DIRECT_MIC=1` is set only when the user opted in **and** the permission
is actually granted. That second condition matters: setting the gate without the permission
would advertise a capture endpoint the AAudio input cannot open, which is precisely the
black-screen condition above. Withholding it degrades to render-only instead.

---

## Verifying on a device

Run with the upstream diagnostics build plus `BANNER_AUDIO_DIRECT_LOG=1` and watch logcat for
the `DirectAudio` tag. The open line reports the geometry AAudio actually granted, which is the
first thing to check when audio is present but wrong:

```
capture open: req 48000/float/2ch preset=voicecomm perf=1 - got rate=48000 ch=1 fmt=2
capture start
```

Upstream validated a real microphone recording and TF2's Options → Voice mic-test meter on an
AYANEO Pocket FIT (Adreno 750, Android 14). Reproduce that first.

---

## Known gaps

Upstream's own honest list, plus what applies on the WinNative side:

| Gap | Detail |
| --- | --- |
| **No capture watchdog** | The render side has a dead-callback watchdog. The capture struct declares `cb_count` and `last_cb_ns`, but they are written and never read — the instrumentation exists, the watchdog does not. |
| **Failed reopen keeps a dead stream** | If reopening the input fails after a route change, nothing retries it and the mic is lost for the session. |
| **Latency floor** | An open input stream may knock the output off the AAudio fast path on some devices. Unmeasured — upstream flags this as the one item that could threaten the latency floor. |
| **Online voice round-trip** | Mic → VAC server → second player has not been validated end to end by anyone. An in-game meter moving proves capture, not transport. |
| **Device spread** | AAudio input under FEX is proven on one device. WinNative's spread is much wider. |
| **Background microphone** | Android 9+ silences mic input for apps with no foreground process, regardless of target SDK. `SessionKeepAliveService` is declared `specialUse\|dataSync` with no `microphone` foreground-service type — fine while the game is foregrounded, silent when it is not. |
| **`targetSdk 28`** | Android 14's foreground-service-type enforcement does not apply at this target. Add the `microphone` FGS type when the target is raised. |

## Updating the bundled driver

1. Download the new release artifacts into `app/src/main/assets/directaudio/` (command in
   `PROVENANCE.md`), and refresh the checksums recorded there.
2. Bump `BUNDLED_VERSION` in `DirectAudioDriver.kt` so existing containers re-overlay.
3. Copy `COPYING`, `NOTICE` and `AUTHORS` again if upstream changed them.

If you ever need to build from source rather than ship his binaries, upstream's `INTEGRATION.md`
has the recipe: add the repo as a submodule at `dlls/winedirectaudio.drv` in the Proton tree —
which is also what keeps his commit history intact — apply ~10 lines to `configure.ac` and
`dlls/mmdevapi/main.c`, then `./configure --enable-archs=arm64ec --with-aaudio`.
