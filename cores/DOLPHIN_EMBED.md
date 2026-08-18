# Embedded Dolphin (Route B2) — WinNative-driven GC/Wii

> **Packaging note (current):** the emucore and its runtime data are no longer committed
> here. They are built in the [WinNative-Emu](https://github.com/WinNative-Emu) fork and
> shipped in the [Retro-Consoles](https://github.com/WinNative-Emu/Retro-Consoles) bundle
> the app downloads at runtime. The build/patch details below describe how that fork
> produces the binary and remain accurate for the fork.

Decision (2026-07-22): replace the dolphin-libretro GLES path for GameCube/Wii with an
embedded standalone Dolphin core, mirroring the ARMSX2/PS2 pattern. WinNative drives
everything — Dolphin's own menus/UI are stripped; every setting, driver and toggle is
fed from WinNative's surfaces (Shortcut Settings, Settings > Retro, in-game drawer).

Why: dolphin-libretro can only render GLES via libretrodroid, and the system Adreno
GLES driver SIGSEGVs on new silicon (see DOLPHIN_VULKAN.md). Standalone Dolphin brings
its own Vulkan backend + native adrenotools/Turnip loading, RetroAchievements, netplay,
and save infrastructure.

## Module: `dolphin/`

- `build-emucore.sh` — clones dolphin-emu/dolphin at pinned SHA
  `b008142f72a099a9bcb971776209504f085ae0be` (+ submodules), applies `patches/`,
  CMake+Ninja (NDK toolchain, arm64-v8a, android-26, flexible page sizes ON,
  analytics/autoupdate/tests OFF, RetroAchievements ON) and builds:
  - `libmain.so` — Dolphin core + JNI
  - `libhook_impl.so`, `libmain_hook.so`, `libgsl_alloc_hook.so`,
    `libfile_redirect_hook.so` — libadrenotools (custom Vulkan driver loading)
- `build.gradle` — library module `org.dolphinemu.dolphinemu`; native build is opt-in
  via `-Pdolphin.buildNative=true` until the pipeline is proven; otherwise packages
  `prebuilt/emucore/`.
- Emulation runs in the `:gc` process via
  `org.dolphinemu.dolphinemu.wn.DolphinEmulationActivity` (WinNative-owned; no
  upstream fragments/menus).

## Phases

Status 2026-07-22: Phases 1–5 done and verified on-device (CPH2749): TP GC boots on
Vulkan crash-free, input proven via chained input-only transitions, settings bridge
verified both directions (stretch applied / auto restored), and **Turnip verified on
Adreno 840** — WN-Turnip-1.06 staged via wn.gc.driver → GpuDrivers/Extracted,
libvulkan_freedreno.so confirmed mapped in-process, clean rendering. GPU Driver
pickers live on both GC/Wii settings surfaces.

Phases 6–7 (2026-07-22, verified on-device): DolphinGameOverlay attaches the shared
WinNative RetroInputView (GC/Wii themes) + drawer menu via the DolphinHost hook
(Ps2GameOverlay pattern; PluviaApp installs in every process, :gc gets light init).
Verified: overlay renders over the game, drawer opens, save state wrote GZ2E01.s01,
Exit → StopEmulation → clean finish. Routing: GC/Wii launches go to embedded Dolphin
in both launch paths behind `wn.gc.embedded` (default true; off = libretro fallback).
Settings parity pass (2026-07-22, compile-verified; device was disconnected):
- **In-game drawer** now has Menu (save/load slots 1–3 with timestamps, Screenshot,
  HUD toggle, Pause/Exit) + Display (ALL GC/Wii core options, live via
  applyVariable → INI rewrite → ReloadConfig) + Sound (audio toggle → [DSP] Muted)
  + Controls (touch on/off, haptic, edit/reset layout; back exits edit mode).
- **Settings hierarchy verified**: Console default (Settings > Retro,
  `retro_def_var_<sys>_<key>` / `retro_def_*`) → Shortcut extra (`retro_var_*` /
  `retro_*`) → launch (resolvedCoreVariables + wn_touch/wn_audio/wn_hud) → in-game
  changes apply live AND persist back to the shortcut. GPU driver = `wn.gc.driver`
  (global for GC+Wii, PS2 pattern).
- **VBI Skip now has a UI toggle** (GC + Wii lists, default On).
- **HUD** maps to Dolphin's native OSD (ShowFPS/VPS/Speed); audio to [DSP] Muted.
- Shader/SGSR/upscale (libretrodroid-only) are hidden on both static surfaces for
  GC/Wii while embedded is enabled; they reappear if `wn.gc.embedded` is off.
- Known quirk: Shortcut Settings save() snapshots ALL option values into the
  shortcut (pre-existing behavior for every console) — after first save, console
  defaults no longer flow to that shortcut.

Start/teardown lifecycle (2026-07-22, compile-verified): the Dolphin core cannot
reliably boot twice in one process, so :gc follows the ARMSX2 model — fresh process
per session. `teardown()` (idempotent; Exit button, back press, Run() returning, and
onDestroy-when-finishing all funnel into it) does StopEmulation → join the Run thread
(≤15s, so memcards/saves flush) → delete `.running_rom` marker → finish() → kill the
:gc process. onPause/onResume skip pausing while stopping (no pause-during-shutdown
races). Launch side: the game activity now JOINS the Library task (NEW_TASK only from
non-activity contexts) so exiting lands back on the Library; `prepareLaunch` kills a
lingering :gc before starting UNLESS the `.running_rom` marker matches the requested
ROM (tapping Play on the running game resumes instead of restarting).

Drawer parity + shared-source pass (2026-07-22, compile-verified):
- GC/Wii drawer now uses the FULL shared tab set via `RetroDrawerTabs.build`
  (Menu / Display / HUD / Sound / Controls; netplay correctly absent).
- **Memory Card save UI**: Save/Load State from the main menu opens the shared
  SaveSlot-card pane (RetroPane.SAVES) with 4 native Dolphin slots titled
  "Memory Card N" + timestamps — same cards/flow as every other console.
- **Controls pane extracted to `RetroControlsMenu`** — single source used by BOTH
  RetroActivity (all libretro consoles, incl. the PSX-inversion gate) and the
  Dolphin overlay: touch toggle, **adaptive sticks (now on GC/Wii too — wn_adaptive
  rides the launch vars)**, haptics, edit/reset layout, and the four button-color
  pickers + reset.
- **HUD tab**: shared `RetroHudSupport.buildHudEntries` + FrameRating with new
  shared container-JSON persistence (`loadContainerHudSettings`/`save…`); master
  toggle also drives Dolphin's native OSD (ShowFPS/VPS/Speed). FPS/graph elements
  default off for Dolphin (no per-frame Java callback; native OSD covers it).
- Remaining dedup candidate: RetroActivity still has its older inline HUD-JSON
  load/save — same schema as the new shared funcs; migrate it next pass.

Settings surfaces + persistence pass 2 (compile-verified):
- Shortcut Settings now has a HUD tab for ALL retro consoles (shared
  RetroLibretroHudSection: global master + element toggles; PS2 keeps its own).
- GPU Driver moved to the TOP of Graphics (shortcut dialog) and top of the
  expanded GC/Wii console-defaults section (extracted DolphinGpuDriverDropdown).
- Persistence model: HUD is now UNIVERSAL (global prefs: retro_def_hud_global +
  retro_hud_style/elements) — one HUD everywhere, edited from any surface or
  in-game; per-shortcut KEY_HUD no longer read. Per-game: core options, touch
  controls, adaptive sticks, audio, shader/upscale (libretro). Per-console:
  L3/R3 visibility, console defaults. RetroActivity + Dolphin overlay both load/
  save the global HUD store (container-JSON path retired to legacy).

Open items: overlay polish (FrameRating-style HUD, touch-IR for Wii ir_mode,
controller-connected hiding, playtime/cloud hooks), RetroAchievements for the
embedded path, defaults tuning (dual core ON), retiring the libretro dolphin core
after parity sign-off. On-device retest checklist when phone reconnects: drawer tabs
render + live-apply (aspect change visible), audio mute, HUD OSD, VBI toggle,
touch-controls hide, edit-layout back handling, Play-button routing end-to-end.

1. **Scaffold** (done): module, build script, manifest, stub activity, app wiring.
2. **Emucore + JNI surface**: build `libmain.so`; vendor the minimal Java classes
   Dolphin's `IDCache`/JNI_OnLoad requires (`NativeLibrary`, `NativeConfig`,
   `GpuDriverHelper`, model classes); patch `IDCache` to drop upstream-UI-only classes.
3. **DolphinEmulationActivity**: SurfaceView → `NativeLibrary.SurfaceChanged`,
   `Run(rom)`, pause/resume, input forwarding, clean shutdown.
4. **Settings bridge**: generate `User/Config/Dolphin.ini` + `GFX.ini` from WinNative
   prefs pre-boot (renderer=Vulkan, IR scale, aspect, dual core, JIT, skip BIOS,
   widescreen, sensor bar, IR mode, cheats); live updates via `NativeConfig` where
   safe. Same keys/surfaces as the current GC/Wii settings (RetroCoreOptions ids map
   1:1 onto Dolphin ini keys).
5. **GPU driver**: reuse `CustomDriver` Turnip packs through Dolphin's
   `GpuDriverHelper`; per-console `wn.gc.driver` pref; default system Vulkan.
6. **Overlay**: `DolphinGameOverlay` (Ps2GameOverlay analogue) — RetroInputView touch
   overlay, WinNative drawer (save states via `NativeLibrary.SaveState/LoadState`,
   live settings, HUD, cheats), playtime stats, exit cloud backup.
7. **Routing**: `RetroShortcuts.launch` sends gc/wii to the embedded activity (toggle
   first, libretro fallback until parity); settings surfaces switch to the Dolphin
   sections; decide native RetroAchievements vs RetroAchievementsManager.

## Phase 2 analysis (done)

Authoritative JNI-required Java surface (every `FindClass` under `Source/Android/jni/`),
30 classes — all model/util, **no upstream UI**:

- `NativeLibrary`
- cheats models: `ARCheat`, `GeckoCheat`, `PatchCheat`, `GraphicsMod`, `GraphicsModGroup`
- input models: `controlleremu/{Control, ControlGroup, ControlGroupContainer,
  ControlReference, EmulatedController, NumericSetting}`, `CoreDevice(+$Control)`,
  `InputDetector`
- netplay: `NetplaySession`, `model/Player`
- `riivolution/model/RiivolutionPatches`, `skylanders/model/SkylanderPair`
- `model/GameFile`, `model/GameFileCache`
- utils: `Analytics`, `AudioUtils`, `BooleanSupplier`, `CompressCallback`,
  `ContentHandler`, `NetworkHelper`, `PermissionsHandler`, `WiiUpdateCallback`
- plus `utils/GpuDriverHelper` (static-native Kotlin side of the adrenotools bridge;
  native impl `jni/GpuDriver.cpp` calls `adrenotools_*` directly — Phase 5 hook point)

Naive transitive closure is 92 files because a few bridge classes import the UI web.
Patch these cut points (in `patches/`), then the closure collapses to ~50 UI-free files:

1. `NativeLibrary.kt` — replace `EmulationActivity`/`AlertMessage` references with a
   `WnHost` callback interface our activity registers (alerts, exit, orientation,
   title changes). This IS the B2 design: UI callbacks go to WinNative.
2. `utils/Analytics.kt` — strip the `FragmentActivity` dialog method (analytics is
   compiled out with `-DENABLE_ANALYTICS=OFF`).
3. `NetplaySession.kt` — sever imports of netplay UI (`NetplaySetupActivity`,
   viewmodels) if present; keep the session/Player model.
4. `Settings.kt`/`NativeConfig` chain — sever `SettingsActivity`/`MenuTag` imports.
5. Drop `DolphinApplication` (not JNI-required); our module provides directory init
   via `DirectoryInitialization` directly.
6. Skylander/Infinity `ui/` adapters fall out once `EmulationActivity` is cut.

Vendoring mechanism: gradle Copy task from `emucore-src/.../java` (post-patch) into
`build/generated/dolphinJava`, added as a srcDir — no duplicated sources in-repo.

## MMJR2 fork review (2026-07-22)

Reviewed Medard22/Dolphin-MMJR2-VBI, nachoverdon/Dolphin-MMJR2, Mato00KAN/Dolphin-MMJR2
for patches worth adopting:

- **VBI Skip (the headline MMJR2-VBI feature, by Sam Belliveau) is already in our
  pinned upstream base** — it was upstreamed into official Dolphin: `bVISkip`
  (VideoConfig.h:307), adaptive gate `CoreTiming::UpdateVISkip/GetVISkip` (only skips
  when the core runs behind real time; auto-off under determinism/netplay), interrupt
  suppression in VideoInterface.cpp:986. Config: `GFX.ini [Hacks] VISkip`; vendored
  `BooleanSetting.GFX_HACK_VI_SKIP` already exposes it to our bridge. Nothing to port.
  **Phase 4: expose as a WinNative "VBI Skip (auto frameskip)" toggle, default ON**
  (adaptive — no cost at full speed; this is the MMJR2 "runs better" effect).
- The other two forks are "MMJR2 rebased onto latest official dev source with the old
  MMJR UI and no scoped storage" — both properties irrelevant to us (we build current
  master, strip all Dolphin UI, and own the data paths via SetUserDirectory).
- No other distinguishing native patches found worth carrying; MMJR-style value comes
  from *defaults*, which our settings bridge already curates (JITARM64, DSP-HLE,
  EFB-to-texture, sync shaders, + VISkip on).

## Notes

- The libretro core (`libdolphin_libretro_android.so`) stays in place until Phase 7
  parity, then is removed along with its jniLibs entry.
- ARMSX2 reference costs: 17k LOC bridge, 1.3k LOC overlay — expect the same order.

Cloud-save bucket fix (2026-07-22): the real reason Save History was blank for
ALL retro consoles — auto-upload used cloudGameId ("retro_gc_<name>") but the
History UI and manual backup query under customGameId ("<containerId>:<file>.desktop")
= two different Google buckets, so nothing ever appeared. Worse, backupSaveToGoogle
resolves the save DIR from the gameId (getCustomSaveSources → findShortcut →
retroSaveDir); a cloudGameId resolves no shortcut, so it found no files and
silently uploaded nothing. Fix: everything now uploads/restores under customGameId
(same as the working PS2 path). retroSaveDir extended to return the full per-game
set: PS2=memcards, GC/Wii=DolphinCloudSync staging (states+GC memcards), other
libretro=RetroSaveStates.gameDir (states+sram). Internal staging dir stays keyed
by cloudGameId (a stable path token) while the Google bucket uses customGameId —
the two IDs have distinct roles. findCustomShortcutByContainerAndFile resolves
retro shortcuts via loadShortcuts() (retroContainer id 0), confirmed.
