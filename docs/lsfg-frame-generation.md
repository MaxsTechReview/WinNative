# LSFG frame generation in WinNative

Review of the two existing Android LSFG integrations, of WinNative's own
renderer, and the design for putting frame generation on the Android side of
the Wine boundary.

## Sources reviewed

| Source | Revision |
|---|---|
| `FrankBarretta/LSFG-Android` | `da867e9` |
| └ `lsfg-vk-android` (submodule, branch of `PancakeTAS/lsfg-vk` 1.0.0) | `b55b182` |
| └ `LSFG-Android-Application` (submodule) | `b847541` |
| `eden-emu/eden` PR #4263 — *[vulkan, android] Initial implementation of LSFG-VK* | `469c9af` (base `dc95cd0`) |
| WinNative | `acc130ee` |

The two clones live outside the repo at `~/Build/lsfg-research/`. The only thing
vendored into WinNative is DXVK's `dxbc` subset (zlib licence), taken from
`lsfg-vk-android/thirdparty/dxbc`.

## 1. What LSFG actually is

Lossless Scaling Frame Generation 3.1 is a fixed-function chain of **compute
shaders** that takes two consecutive presented frames and synthesises one or
more intermediate frames by estimating a dense optical flow field between them
and warping along it. There is no neural network runtime and no external
dependency — it is 25 compute shaders and a pile of intermediate images.

The shaders themselves are **not redistributable**. They ship as `RCDATA`
resources inside `Lossless.dll` from the user's own paid copy of Lossless
Scaling. Every implementation — upstream `lsfg-vk`, LSFG-Android, and eden —
requires the user to supply their own DLL and extracts the resources on-device.
None of them bundle it, and neither can WinNative.

The chain, as reconstructed in eden (`lsfg_*.h`):

| Stage | Shader IDs | Shape | Role |
|---|---|---|---|
| `mipmaps` | 255 | 7 levels | Luma pyramid of the input frame pair |
| `alpha` | 290–293 | 4 stages × 7 mip levels | Coarse-to-fine flow estimate, 3-deep history |
| `beta` | 298–302 | 5 stages, 6 outputs | Flow refinement |
| `gamma` | 280, 282–285 | 5 stages × 7 mip levels | Flow upsampling / confidence |
| `delta` | 280–281, 286–289, 294–297 | 10 stages × 3 instances | Occlusion + warp field |
| `generate` | 256 | 1 per generated frame | Final warp/blend into the target image |

Two knobs matter for cost: **flow scale** (0.25–1.0, resolution of the flow
pyramid relative to output) and **multiplier** (2×–4×, how many frames are
generated per real frame). Only `generate` runs per generated frame; everything
above it is shared across all generations from the same frame pair, which is why
3× costs far less than 1.5× the cost of 2×.

Shader variants: the base chain IDs above hold **DXBC**. Lossless Scaling
**3.2.2** added precompiled SPIR-V copies of the same shaders at `base + 49`
(native fp16, IDs 304–351) and `base + 98` (native fp32, IDs 353–400) — its
release note reads, in full, "Added shaders intended for use by the lsfg-vk
project."

Those precompiled blobs are what make a DXBC translator unnecessary: eden's
`IsSpirvModule` / `AdoptSpirvModule` just re-numbers the descriptor bindings in
set/binding order and hands the words to `vkCreateShaderModule`, and eden
dropped the translator outright (commit `6dd3098 Remove requirement on dxbc`).
Upstream `lsfg-vk` still carries DXVK's `dxbc` because it also supports older
builds — `src/extract/trans.cpp` runs `dxvk::DxbcModule` over the base IDs.

**Those blobs are not obtainable from Steam today, so a DXBC translator is
required.** Measured on device against a fresh download of the current build:

- `Lossless.dll` (5,435,904 bytes, FileVersion 3.2.1.0) carries RCDATA IDs
  101–302, **all 202 of them DXBC**, and none of the `+49`/`+98` variants.
- Scanning the **entire 311 MB / 456-file install** for the SPIR-V magic word
  returns **zero occurrences** — the blobs are not hiding in another file.
- The public branch is `buildId 19655272`, last updated 2025-08-19, and the
  installed manifests match its PICS gids exactly, so this *is* the current
  build, not a stale download. Every other branch (`beta`, `linux_testing`,
  `legacy_*`) is older; `linux_testing` is byte-identical to `public` on depot
  993091. There is nothing newer to fetch.

This matches what the working Android implementation actually does: upstream
`lsfg-vk` and its Android fork run the **DXBC path by default**, linking
DXVK's `dxbc` in `src/extract/trans.cpp`, and treat the precompiled SPIR-V as
an opt-in FP16 toggle for Mali parts that lack `vulkanMemoryModel`. Eden's
translator-free path assumes a Lossless build that carries the blobs; that
assumption does not hold for anything currently downloadable.

Eden's own UI strings confirm the shape of what it consumes: *"This GPU driver
does not support the Vulkan memory model, which the Lossless Scaling shaders
require."* `VulkanMemoryModel` is emitted by DXVK's translator and is absent
from the GLSL450 FP16 blobs — so eden is running DXVK-translated SPIR-V too, it
just gets it pre-translated when the DLL supplies it.

**Implemented: both producers, one consumer.** Eden's design has a clean seam —
everything below `ShaderModules` (a map of shader id → SPIR-V words) is
source-agnostic. So the SPIR-V path is kept exactly as eden has it, and DXVK's
`dxbc` (zlib licence, 22.7k lines, vendored under `cpp/thirdparty/dxbc` from the
same subset `lsfg-vk` uses) fills the same map when the variants are absent.
`lsfg_dxbc.cpp` follows `lsfg-vk`'s `trans.cpp` exactly, including its
encounter-order binding renumber, which is what pairs with DXVK output;
eden's set/binding sort stays on the precompiled path where it belongs.

Validation now matches eden's `ParseShaderSpans` semantics too: a DLL is valid
when the **base chain IDs** are present, with no requirement that the SPIR-V
variants exist.

Measured end to end on an Adreno 750, translating the user's own 3.2.1 DLL:

- **25/25 modules translated**, 352,889 SPIR-V words (1.38 MB), in ~40 ms.
- **25/25 pass `spirv-val --target-env vulkan1.3`**.
- Emitted modules are **SPIR-V 1.6**, `OpMemoryModel Logical Vulkan`, with
  `OpCapability VulkanMemoryModel`, `StorageImageWriteWithoutFormat` and
  `ImageQuery`; bindings renumber to a dense 0..n range as intended.

That last point is a hard runtime requirement and the probe now enforces it:
Vulkan **1.3** (SPIR-V 1.6 will not load on a 1.1 device), plus
`vulkanMemoryModel`, `shaderStorageImageWriteWithoutFormat` and
`shaderStorageImageExtendedFormats`. A device failing any of them reports
unsupported up front instead of failing at `vkCreateShaderModule`.

## 2. How LSFG-Android bakes it in — and why WinNative must not copy it

LSFG-Android is a *standalone overlay app*. It cannot see into another app's
Vulkan swapchain, because Android 12+ blocks loading external code into
non-debuggable processes, so there is no equivalent of Linux's implicit layer
mechanism. Its pipeline is therefore:

```
target game → SurfaceFlinger → MediaProjection capture → app's VkDevice
  → AHardwareBuffer → framegen's *own* VkDevice → AHB back
  → SYSTEM_ALERT_WINDOW / accessibility overlay composited over the game
```

Its own README puts the cost at **50–80 ms of added latency versus the Linux
Vulkan layer**, and calls it a platform constraint rather than a bug. It also
needs `SYSTEM_ALERT_WINDOW` + screen capture + an `AccessibilityService`, which
is a Play-policy violation and a per-session consent prompt.

Two structural details are worth carrying forward even though the model is not:

- **`vkGetMemoryFdKHR` fails on AHB-imported memory on both Adreno and Mali.**
  Upstream `lsfg-vk`'s FD-based image sharing is unusable on Android; the fork
  replaced it with a direct `AHardwareBuffer*` path
  (`VK_ANDROID_external_memory_android_hardware_buffer` +
  `VkImportAndroidHardwareBufferInfoANDROID` with a dedicated allocation).
- **`framegen` owns its own `VkInstance`/`VkDevice`.** Cross-device
  synchronisation is not expressible with Vulkan semaphores, so the fork added a
  `waitIdle()` entry point that calls `vkDeviceWaitIdle`. Consuming the library
  as-is means a **full device stall per frame**.

That second point is decisive. Linking `lsfg-vk-android`'s `framegen` into
WinNative would give us a second Vulkan device, AHB round-trips in both
directions, and a `vkDeviceWaitIdle` on the critical path — inside a compositor
that already has the frame sitting in a `VkImage` on the right device. The
library's API surface exists to serve a process that *cannot* reach the game's
images. WinNative can.

## 3. How eden PR #4263 bakes it in — the right model

Eden reimplements the LSFG chain **inside the emulator's own Vulkan renderer**:
same `VkInstance`, same `VkDevice`, same `Scheduler`, same command stream. No
second device, no AHB, no `waitIdle`. `~4700` lines across `src/video_core/`.

The integration contract is small (`renderer_vulkan.cpp::Composite`):

```cpp
blit_swapchain.DrawToFrame(device, rasterizer, frame, framebuffers, ...);

void(frame_gen.WantedGenerations(present_manager.MaxExtraFrames()));
frame_gen.Process(device, frame, swapchain.GetImageFormat(), GuestExtent(framebuffers));

for (size_t generation = 0; generation < frame_gen.GeneratedFrameCount(); ++generation) {
    Frame* generated = present_manager.GetRenderFrame();
    blit_swapchain.PrepareFrame(device, generated, render_window.GetFramebufferLayout());
    frame_gen.GenerateInto(device, generated, generation);
    scheduler.Flush(*generated->render_ready);
    present_manager.Present(generated);
}

scheduler.Flush(*frame->render_ready);
present_manager.Present(frame);
```

The pieces that make it work:

- **Composite into an owned off-screen image, not the swapchain image.**
  `Frame::image` gains `VK_IMAGE_USAGE_STORAGE_BIT` (guarded by a
  `VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT` probe) plus `TRANSFER_DST` and
  `SAMPLED`, and a matching `storage_view`. `generate` writes straight into it,
  and the existing `CopyToSwapchain` blit is unchanged. This sidesteps the fact
  that Android swapchain images are almost never storage-capable.
- **A 2-deep input ring.** `Process` copies the just-composited frame into
  `chain->Input(count)` and dispatches the shared part of the chain. Frame N−1 is
  still in the other slot.
- **Generated frames are presented *before* the real frame.** Interpolation
  produces frames that belong between N−1 and N, so N is held back one slot.
  This is the source of the added latency, and it is unavoidable for any
  interpolating (as opposed to extrapolating) generator.
- **Present queue depth is raised to make room**: `image_count` becomes
  `clamp((generations + 1) * (queue_target + 1), swapchainImages, 7)`.
- **`FrameGenPacer` decides the multiplier at runtime** rather than trusting the
  setting. It smooths the real frame interval (EMA 0.25), rejects burst frames,
  requires a 1 s stabilisation window, and periodically *probes* one extra
  generation — keeping it only if measured throughput improves by ≥1.15× and the
  base rate does not collapse below 0.70×, with 5/15/30/60 s backoff on repeated
  failures. This is the part that stops frame gen from making a GPU-bound game
  slower, and it is worth porting behaviourally rather than reinventing.
- **A two-frame warm-up** (`LSFG_REQUIRED_FRAMES` + `LSFG_RECURRENCE_FRAMES`)
  before anything is emitted, so the history slots are valid.

Android-side plumbing in the same PR that we would need an analogue of:
`LosslessScalingHelper.kt` (SAF pick → copy to internal storage →
`prepareLosslessDll()` → cache build → delete the copy), `LosslessManagerFragment`,
and a `supportsFrameGeneration()` capability probe.

**Licence note:** eden is GPL-3.0-or-later and so is WinNative (`LICENSE`, GPLv3).
Eden's `lsfg_*` files carry dual `Eden Emulator Project` / `lsfg-vk` GPL-3.0
headers. Porting them into WinNative is licence-compatible provided the
copyright headers are preserved verbatim. Upstream `lsfg-vk` itself is MIT, so
the algorithm is also reachable from the MIT side if we ever want a looser
licence — but the eden tree is the one that already works without DXBC.

## 4. WinNative's renderer, traced

A Wine game's frame reaches the display like this:

1. **Guest**: DXVK (or wined3d/OpenGL-on-Zink) renders with the guest Vulkan
   driver inside the container.
2. **DRI3 handoff**: the guest presents to its X11 window via
   `PixmapFromBuffers` carrying the private Android modifier
   (`ANDROID_NATIVE_BUFFER_MODIFIER`), whose single ancillary FD is an
   `AHardwareBuffer` socket handle — `DRI3Extension.java:41` / `:203`.
3. **Host import**: the Java X server imports it as a `GPUImage`, and
   `GPUImage.nativeImportAhbToVulkan` → `vkr_texture_import_ahb`
   (`vk_image.c:1116`) turns it into a real `VkImage` in **WinNative's own
   `VkDevice`**, zero-copy.
4. **Composite**: `VulkanRenderer.java` snapshots the scene into a ~2.8 KB
   `ByteBuffer` and calls `nativeRenderFrame` on the `XServerSurfaceView` render
   thread. `record_and_submit_frame` (`vk_renderer.c:2096`) acquires a swapchain
   image, runs `draw_scene_pass`, walks the effect chain through ping-pong
   `VkOffscreen` targets (SGSR1, CRT, HDR, …), and ends the last render pass
   directly in the swapchain framebuffer.
5. **Present**: one `vkQueueSubmit`, one `vkQueuePresentKHR` (`:2431`).

Relevant current parameters:

- `VK_FRAMES_IN_FLIGHT` is **2**; swapchain is `minImageCount + 1` (3 on most
  devices), capped at `VK_MAX_SWAPCHAIN_IMAGES` = 8.
- Swapchain `imageUsage` is `COLOR_ATTACHMENT_BIT` only (`+ TRANSFER_SRC` when
  the recorder is active) — **not storage-capable**.
- Default present mode is `VK_PRESENT_MODE_FIFO_KHR`, settable via
  `nativeSetPresentMode`.
- Render mode is `RENDERMODE_WHEN_DIRTY`. The render thread is woken by
  `requestRenderCoalesced()`, which posts a Choreographer callback, so the
  compositor's present cadence is already slaved to the game's update cadence.
- FPS pacing is *not* done by sleeping in the renderer — it is back-pressure on
  `PresentIdleNotify` in the X Present extension (`vk_renderer.c:3027`).

**The key finding: the game's finished frame is already a `VkImage` in
WinNative's own Vulkan device, on the Android side of the Wine boundary, before
anything is presented.** WinNative is structurally in eden's position, not
LSFG-Android's. Everything MediaProjection exists to work around is already
solved here by DRI3+AHB.

## 5. Design

Put the LSFG chain in `vk_renderer.c`'s device, driven from
`record_and_submit_frame`. Nothing crosses into Wine; nothing touches
MediaProjection; no second `VkDevice`.

```
guest DXVK ──DRI3/AHB──> VkImage (host) ──> draw_scene_pass ──> effect chain
                                                                     │
                                                       ┌─────────────┴──────────────┐
                                                       ▼                            ▼
                                              composite target                 LSFG input ring
                                              (STORAGE|SAMPLED|                (2 × R8G8B8A8)
                                               TRANSFER_SRC/DST)                     │
                                                       │                    shared chain: mipmaps
                                                       │                    → alpha → beta
                                                       │                    → gamma → delta
                                                       │                             │
                                                       │                    generate × N ────┐
                                                       │                                     │
                                                       ▼                                     ▼
                                            present(real frame N)  ◄── after ── present(gen 0..N-1)
```

### 5.1 Redirect the composite off the swapchain

Today the last render pass writes straight into
`r->swapchain_framebuffers[image_index]`. Frame gen needs the composited result
to be *readable* (it becomes next frame's LSFG input) and needs generated frames
to be *storage-writable*. Both fail on Android swapchain images.

Add a `VkCompositeTarget` ring — same shape as the existing `VkOffscreen` but
with `STORAGE | SAMPLED | TRANSFER_SRC | TRANSFER_DST | COLOR_ATTACHMENT`, sized
to the swapchain extent, `(max_generations + 1) × (queue_target + 1)` deep. When
frame gen is off, the existing direct-to-swapchain path stays exactly as it is —
this must be a zero-cost bypass, not a new mandatory copy.

With frame gen on, the final effect pass (or `draw_scene_pass` when there are no
effects) targets a composite image, and a trivial blit moves it into the acquired
swapchain image. That blit is the same operation the recorder already performs at
`vk_renderer.c:2346`, so the code pattern exists.

### 5.2 The chain

Port eden's `lsfg_*` files to C in `app/src/main/cpp/winlator/vk/lsfg/`,
preserving the GPL-3.0 headers. The port is mostly mechanical — eden's
`LsfgImage`/`LsfgPass`/`LsfgBarriers`/`LsfgDescriptorWriter` are thin RAII
wrappers over exactly the objects `vk_state.h` already models by hand, and
`vk_renderer.c` already has `vkr_image_barrier`, a descriptor pool with
`vkr_free_descriptor_set`, and a suballocator.

Compute pipelines are new to this renderer — everything today is graphics
pipelines with a fullscreen triangle — so `create_pipelines` needs a compute
path, and the device needs `VK_DESCRIPTOR_TYPE_STORAGE_IMAGE` pool capacity.

### 5.3 Presenting the extra frames

The natural fit is to keep **one `nativeRenderFrame` call per real frame** and
emit the extra presents inside it. With `VK_PRESENT_MODE_FIFO_KHR`, queuing
N+1 presents in one go makes the driver display them on N+1 consecutive vblanks
— the pacing falls out of FIFO for free, with no sleeps and no render-mode
change on the Java side.

Ordering per real frame, matching eden:

1. Composite frame N into a composite target.
2. Copy it into the LSFG input ring, dispatch the shared chain.
3. For `g` in `0..N_gen-1`: acquire a swapchain image, `generate` into a
   composite target, blit to the swapchain image, submit, present.
4. Acquire, blit composite N to the swapchain image, submit, present.

Consequences to handle:

- `VK_FRAMES_IN_FLIGHT` must rise from 2 to at least `max_generations + 2`, and
  swapchain `minImageCount` must be requested as
  `clamp((generations + 1) * (queue_target + 1), minImageCount + 1, 8)`.
- `vkAcquireNextImageKHR` is called N+1 times per real frame, so each pending
  present needs its own `image_available`/`render_finished` semaphore and fence
  — the current per-`frame_index` arrays need to be indexed per *present*, not
  per composite.
- The Present-extension back-pressure at `vk_renderer.c:3027` releases guest
  buffers based on presents. It must count **real** presents only, or the guest
  will be told it can render N× faster than it can and the pacer will fight
  itself.

### 5.4 Pacing

Port `FrameGenPacer` behaviourally. Its value is not the multiplier arithmetic
but the probe/backoff loop: on a phone SoC the LSFG chain competes with the game
for the same GPU, and a naive fixed 3× on a GPU-bound title lowers the real frame
rate more than the generated frames add. The probe measures that and backs off.

WinNative-specific inputs the pacer should use that eden does not have:
`currentFpsLimit`, the display's actual refresh rate (never generate above it),
and thermal state — sustained frame gen on a phone is a thermal decision as much
as a perf one.

### 5.5 What gets interpolated

LSFG must see the **game content**, not the whole composited desktop. In
WinNative's favour: the touch-control overlay, `MangoHudView`, and the drawer are
Android views layered over the `SurfaceView`, so they are already outside the
Vulkan composite. Inside it, the software cursor and the effect chain are not.

- Run frame gen **after** the effect chain, so SGSR/CRT/HDR output is what gets
  interpolated and generated frames match the real ones visually.
- The software cursor should be excluded and re-drawn per present, otherwise it
  ghosts. `draw_scene_pass` already draws it separately from windows, so this is
  a matter of splitting the pass, not a redesign.

### 5.6 `Lossless.dll` acquisition — a WinNative advantage

Eden and LSFG-Android both need the user to hand over a DLL through SAF from a
Windows machine. WinNative runs Windows games and ships Steam integration, so
Lossless Scaling can be **installed into the container from Steam directly**, and
the DLL located at
`<container>/drive_c/Program Files (x86)/Steam/steamapps/common/Lossless Scaling/Lossless.dll`.

Offer both: auto-detect inside the container drives, with the SAF picker as the
fallback. Extraction, SPIR-V adoption and caching happen once on the Android side
(cache keyed on file size + hash + variant, as eden does); the DLL is never
loaded or executed, only parsed.

Which producer ran is recorded in the cache header and surfaced as the variant
(`spirv-fp16`, `spirv-fp32`, `dxbc-translated`), so the source is visible in
diagnostics without changing anything downstream.

### 5.7 Settings surface

Per-container and per-shortcut, alongside the existing graphics options, gated
on a capability probe (compute + storage-image support + the DLL being present):
enable, multiplier (2×–4×), flow scale (auto / 25–100 %), fp16 preference,
queue depth, and a target rate cap.

## 6. Latency — the honest accounting

The premise that moving frame gen to the Android side "would actually help with
the input latency" needs one correction, because it changes what we should
promise users.

**Interpolating frame generation always adds latency.** Real frame N cannot be
shown until the frames synthesised between N−1 and N have been shown first. At
2× the penalty is one output interval; at 60 Hz that is ~16.7 ms. No placement of
the code changes this — it is inherent to interpolation. LSFG does not reduce
input-to-photon latency, and enabling it will make a game feel *slightly* less
responsive while looking substantially smoother.

What the Android-side placement does buy is large, though:

| | Wine-side layer | LSFG-Android overlay | **WinNative Android-side** |
|---|---|---|---|
| Vulkan devices | 2 (guest + host) | 2 + capture | **1** |
| Copies of each generated frame | AHB export → X pixmap → import → composite | capture → AHB → AHB → overlay composite | **1 blit to swapchain** |
| Guest round-trips per generated frame | 1 full DRI3/Present cycle | n/a | **0** |
| Frame-gen code runs under | box64/FEX or arm64ec translation | native | **native** |
| Cross-device sync | semaphores + protocol | `vkDeviceWaitIdle` per frame | **barriers in one command buffer** |
| Added latency vs. no frame gen | interpolation + ~2 protocol round-trips | interpolation + 50–80 ms | **interpolation only** |

So the correct claim is: Android-side placement makes frame generation cost
*only* its unavoidable interpolation delay, instead of that plus a capture
pipeline (LSFG-Android) or plus a guest round-trip per generated frame
(Wine-side layer). It is the difference between frame gen being usable and not.

There is also a genuine smoothness win that is not latency: today the
compositor's present cadence is slaved to the guest's update cadence, so a 30 fps
game produces 30 presents/sec and the whole surface — including scrolling and
cursor motion — updates at 30 Hz. Frame gen decouples display cadence from game
cadence.

If reducing input latency is the actual goal, the levers are elsewhere and worth
tracking separately: present mode (`MAILBOX` vs `FIFO`), the
`PresentIdleNotify` back-pressure depth, DXVK's `maxFrameLatency`, and the
Choreographer coalescing in `requestRenderCoalesced`.

## 7. Work plan

| Phase | Work | Verifiable by |
|---|---|---|
| 1 | `Lossless.dll` PE resource walk, SPIR-V adoption, on-device cache, capability probe, container auto-detect + SAF fallback | 25 modules cached; status surfaces in settings |
| 1b | DXBC→SPIR-V translation for the base chain IDs (vendor DXVK's `dxbc`, as `lsfg-vk` does) | **Done** — 25/25 translated and `spirv-val` clean on device |
| 2 | Composite-target ring + swapchain blit, behind an off-by-default flag | Pixel-identical output, no measurable cost with the flag off |
| 3 | Compute pipeline support + `mipmaps`/`alpha`/`beta`/`gamma`/`delta`/`generate` port | Flow-pyramid debug dump matches eden's on the same input pair |
| 4 | Multi-present per composite; semaphore/fence rework; `VK_FRAMES_IN_FLIGHT` and swapchain depth; real-present-only back-pressure | 2× shows 2 presents per guest frame; no validation errors |
| 5 | Pacer with probe/backoff, refresh-rate ceiling, thermal input | 3× on a GPU-bound title backs off instead of losing real frames |
| 6 | Settings UI, per-container/per-shortcut persistence, HUD counters (real vs total) | End to end on device |

Phases 1 and 2 are independent and both land safely with frame gen disabled.

## 8. Risks

- **Storage-image format support.** Generated frames are written by a compute
  shader into `B8G8R8A8_UNORM`/`R8G8B8A8_UNORM`. Probe
  `VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT` and disable frame gen rather than
  failing at pipeline creation, exactly as eden's `CanStoreToFrame` does.
- **Adreno 6xx and Mali.** LSFG-Android reports end-to-end frame gen working on
  Adreno 7xx-class and newer. The chain is 25 dispatches over a 7-level pyramid;
  on older parts the shared chain alone may exceed the frame budget. The pacer's
  probe handles this gracefully, but the capability gate should be conservative.
- **Memory.** 7 mip levels × alpha history × beta/gamma/delta temporaries at
  swapchain resolution is a real allocation. Flow scale is the mitigation and
  should default to auto.
- **Interaction with SGSR1.** Frame gen must sit after upscaling, or it
  interpolates at the wrong resolution and the upscaler re-processes synthesised
  content.
- **Recorder.** `GameRecorder` currently blits every presented frame. It should
  capture real frames only, or recordings get generated frames at an
  inconsistent cadence.
- **Licence hygiene.** Preserve eden's and lsfg-vk's GPL-3.0 headers on every
  ported file. Never bundle, download, or cache-and-redistribute any part of
  `Lossless.dll`.
