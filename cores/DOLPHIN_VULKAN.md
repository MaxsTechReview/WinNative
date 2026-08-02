# Scope: routing Dolphin (GC/Wii) through Vulkan + Turnip

> **Packaging note (current):** the emucore and its runtime data are no longer committed
> here. They are built in the [WinNative-Emu](https://github.com/WinNative-Emu) fork and
> shipped in the [Retro-Consoles](https://github.com/WinNative-Emu/Retro-Consoles) bundle
> the app downloads at runtime. The build/patch details below describe how that fork
> produces the binary and remain accurate for the fork.

## Why

On Snapdragon 8 Elite Gen 5 / Adreno 840 (device CPH2749, driver `libGLESv2_adreno.so`
v0842.41), the Dolphin libretro core reliably SIGSEGVs ~13s into 3D rendering:

```
#00 __memmove_aarch64_nt        libc.so
#01 libGLESv2_adreno.so +9444   ← system Adreno GLES driver faults
#02..#08 libdolphin_libretro_android.so   ← Dolphin OGL backend
#09 <anonymous>                 ← JITARM64 game code (single-core, "CPU-GPU thread")
```

The fault is *inside* the system Adreno GLES driver's vertex-streaming path — not app
code, and not fixable from our GLES side. The durable fix is to stop using the GLES
backend for Dolphin and use its Vulkan backend instead, optionally on the Turnip
(Mesa) driver, which the app already ships for PS2/Wine.

## What we confirmed

- **Core is Vulkan-ready.** The prebuilt `.so` contains the full Vulkan backend
  (`VKGfx`/`VKSwapChain`/`VulkanContext`/`VulkanLoader`) AND the libretro Vulkan HW
  interface shim (`DolphinLibretro/Vulkan.cpp`). `HAS_VULKAN` is on.
- **Core contract (RetroArch Vulkan model):** on `GET_PREFERRED_HW_RENDER` returning
  `RETRO_HW_CONTEXT_VULKAN`, the core:
  1. `SET_HW_RENDER` with `context_type = VULKAN`, `version = VK_API_VERSION_1_0`.
  2. `SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE` → gives us
     `retro_hw_render_context_negotiation_interface_vulkan` (`GetApplicationInfo`,
     `CreateDevice`). The **frontend creates the `VkInstance`**, the **core creates the
     `VkDevice`** (picks GPU, features, extensions) via its `CreateDevice`.
  3. On `context_reset`, core calls `GET_HW_RENDER_INTERFACE` expecting a
     `retro_hw_render_interface_vulkan` from us (instance, gpu, device, queue,
     `get_*_proc_addr`, `set_image`, `set_command_buffers`, `lock/unlock_queue`,
     `get_sync_index`, `wait_sync_index`).
  4. Each frame: core renders into its own `VkImage`, calls `set_image(...)`; the
     frontend presents it.
- **The blocker is 100% frontend.** libretrodroid is GLES-only:
  - `GLRetroView` extends Android `GLSurfaceView` + `setEGLContextClientVersion(3)`
    (`GLRetroView.kt:50,90`). GLSurfaceView cannot host Vulkan.
  - `environment.cpp:265` hardcodes `GET_PREFERRED_HW_RENDER = OPENGLES3`;
    `libretrodroid.cpp:369` rejects anything that isn't GLES3.
  - The compositor (shader chain: Default/CRT/LCD/Sharp/SGSR, viewport, rotation,
    aspect) is entirely GLES (`video.cpp`, `renderers/es3/*`, `ShaderManager`).
- **Turnip plumbing already exists.** `app/src/main/cpp/adrenotools/` +
  `com.armsx2.CustomDriver` download `libvulkan_freedreno.so` and
  `adrenotools_open_libvulkan(...)` returns a hooked `libvulkan.so`. PS2/ARMSX2 already
  uses it. A retro Vulkan backend would load its instance through the same handle.

## Work required

### Route A — add a Vulkan HW-render backend to libretrodroid (the "proper" fix)

**A1. Surface / thread (structural).** Replace GLSurfaceView with a plain `SurfaceView`
+ `ANativeWindow` and a manual render thread for the Dolphin path. libretrodroid assumes
GLSurfaceView + GLThread + EGL throughout; this must become backend-selectable at launch
(new `GLRetroViewData` flag), keeping every other core on the untouched GLES path.
*Largest structural risk.*

**A2. Vulkan HW interface (`libretro_vulkan.h`).** New native module (~1500–2500 LOC,
comparable to a scoped-down RetroArch `vulkan_common.c`):
- Create `VkInstance` (via adrenotools/Turnip handle or system libvulkan).
- Accept VULKAN in `SET_HW_RENDER`; store the negotiation interface; run negotiation
  (`CreateDevice`) → device, queues, `VkSurfaceKHR` from the ANativeWindow.
- Implement the full `retro_hw_render_interface_vulkan` (set_image, queue locking,
  sync-index tracking, command buffers).
- Swapchain create/recreate/present; receive the core `VkImage` and blit/letterbox to
  the swapchain.
- Drive `context_reset`/`context_destroy` on the Vulkan timeline.

**A3. Compositor.** GLES shader chain does not run in Vulkan mode. Minimum viable:
present the core image directly with correct aspect/rotation/viewport (Dolphin has its
own internal-res scaling; shaders/SGSR are a nicety). Full parity = SPIR-V ports of the
shader passes (large; defer).

**A4. Turnip wiring (mostly done).** Reuse `adrenotools_open_libvulkan`; add a Dolphin
GPU-driver picker mirroring the PS2 one (`RetroDefaultsScreen`/`RetroGameSettings`
already have the pattern) → new pref, e.g. `retro_def_var_gc_driver` / `_wii_`.

**A5. Routing / settings.** Only GC/Wii use the Vulkan backend; add the driver picker
(System Vulkan vs installed Turnip) to the three GC/Wii settings surfaces.

### Route B — embed standalone Dolphin (mirror the PS2/ARMSX2 pattern)

Instead of retrofitting a GLES-only libretro frontend, embed a standalone Dolphin build
(its own Vulkan renderer + adrenotools, own SurfaceView, separate process) exactly like
ARMSX2 is embedded for PS2 (`NativeApp.renderVulkan()`, `CustomDriver`, `Ps2GameOverlay`).
- **Pro:** reuses the *working* PS2 Turnip path; no fork of libretrodroid; Dolphin's own
  mature Vulkan+driver handling.
- **Con:** a second Dolphin engine to ship/maintain; GC/Wii lose the libretro features
  wired this session (RetroAchievements incl. hardcore, netplay, unified save-states,
  shared retro UI/overlays) unless re-bridged.

## Route B in detail (embed standalone Dolphin, mirror ARMSX2/PS2)

**Reference cost — what the ARMSX2/PS2 embed actually is (measured in-repo):**
- `armsx2/emucore-src` = **5.9 GB** source (a PCSX2 fork), built from source via
  `build-emucore.sh` (git clone + NDK/CMake/ninja).
- **17,382 LOC** Kotlin/Java bridge in `armsx2/src/main/java`.
- `Ps2GameOverlay.kt` alone = **1,341 LOC** — just the overlay + settings/achievements/
  OSD/dev9 bridge that maps the WinNative drawer onto the native emu.
- Its own `:ps2` process activity (`com.armsx2.Main`) + a large `NativeApp` JNI surface
  (settings, achievements, OSD, patches, netplay).

A `:dolphin` module is the **same class of effort** (vendor a multi-GB emulator, JNI,
process, overlay). The mitigating fact: **upstream Dolphin already has, natively,**
Vulkan, adrenotools/Turnip ("GPU Driver Manager" in Graphics settings — K0bin's
libadrenotools integration), RetroAchievements (incl. hardcore), netplay, save
states/memcards/Wii saves, and AR/Gecko cheats. So Dolphin gives the *features* for free;
the cost is the *bridge/UX*, which splits Route B in two:

- **B1 — bolt-on (small bridge).** Launch GC/Wii into Dolphin's *own* Android UI (reuse
  the `launchEmbeddedPs2` hand-off pattern; pass ROM + driver). Cheapest path to a
  working, non-crashing, Turnip-capable GC/Wii. **But** it *discards this session's entire
  WinNative GC/Wii integration* (shared drawer, HUD, `RetroInputView` overlays, the
  settings just wired, libretro RetroAchievements/netplay) — GC/Wii would look and behave
  like a different app, and you maintain a second full emulator + its multi-GB build.
- **B2 — unified (ARMSX2-scale bridge).** Embed Dolphin's core and rebuild the WinNative
  drawer/HUD/overlay/settings bridge on top (the ~10k-LOC `Ps2GameOverlay`+`NativeApp`
  analogue). Preserves UX parity; **largest effort of all options.**

**Key contrast with Route A:** Route A swaps only the render backend *under* libretrodroid,
so it **keeps 100% of this session's WinNative GC/Wii work** (menus, overlays, HUD,
achievements, netplay) — only rendering changes. Route B1 throws that away; Route B2
rebuilds it.

## Risks to validate BEFORE building (cheap gates)

1. **Does system Vulkan alone dodge the crash?** Turnip may be unnecessary — just moving
   Dolphin off the GLES backend onto the stock Adreno *Vulkan* driver may fix it. If so,
   Route A without Turnip is the fix and Turnip is optional polish.
2. **Does Turnip support Adreno 840 (A8xx / SM8850 "canoe")?** Mesa/Turnip lags on newest
   silicon. **Cheap check: run a PS2 title with a Turnip driver on THIS device** — if PS2
   renders on Turnip, A840 Turnip works and Route A/B-with-Turnip is viable here.
3. **libretrodroid is third-party (swordfish).** A Vulkan backend is a hard fork; future
   upstream merges get harder. Route B avoids this.

## Recommendation

- Gate first (risks 1 & 2) — a few minutes, no code: try Dolphin `renderer=Software`
  (proves GPU driver is the culprit) and a PS2+Turnip run (proves A840 Turnip).
- If system Vulkan fixes it: **Route A** is the clean long-term answer but is a
  multi-week, ~2–4k-LOC native effort with the GLSurfaceView→Vulkan structural change as
  the main risk. **Route B** is likely less code and directly reuses the proven PS2 path,
  at the cost of a second engine and re-bridging libretro features.
- Recommended sequence: validate gates → prototype Route A's Vulkan instance-on-Turnip +
  present-only compositor (no shaders) behind a launch flag for GC/Wii only → measure
  stability → decide whether to invest in the full compositor or pivot to Route B.
