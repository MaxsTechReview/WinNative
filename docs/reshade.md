# ReShade for WinNative — drop-in effects, catalog, multi-effect loadouts + live in-game control

Adds **ReShade (`.fx`) effects** to Vulkan-backed (DXVK/VKD3D) games, with a per-game / per-container
**loadout** (an ordered set of effects), an **in-app catalog** to browse/download/delete effects, and
**live in-game control** — toggle, switch, stack, and tune parameters from the drawer without a
relaunch.

Built on **DadSchoorse/vkBasalt** (zlib — the on-device `.fx`→SPIR-V compiler + Vulkan layer already
bundled in `assets/graphics_driver/extra_libs.tzst`), brought into the Winlator/Cmod lineage by
**Pipetto-crypto**. The only new binary is a small **live-reload patch** on that same `libvkbasalt.so`
(see *Patched layer* below).

## What a user gets

- A **ReShade** section in per-game and per-container settings (its own sidebar entry) that is a
  **loadout editor**: add effects from the catalog, reorder them, remove them, pick a **mode**
  (**Solo** = one active at a time, live A/B switch; **Stack** = layer any subset, applied in chain
  order), and tune each effect's reflected `ui_*` parameters.
- An **in-app catalog** (`reshade.json`): search, download (verified), and **delete** installed
  effects. Effects are self-contained drop-in folders under `Android/data/<pkg>/files/ReShade/<name>/`.
- A **ReShade drawer pane** in-game (`XServerDrawerReshadePane.kt`, its own file alongside the other
  reorganized panes) with a master enable, the Solo/Stack selector, and a row per loadout effect —
  each with its own enable toggle and parameter sliders. **All changes apply live.**

## How live control works (no recompile)

Every effect in the loadout is compiled into the vkBasalt chain **at launch**
(`effects = e1:e2:…`, each with a `<key>_enabled` gate and its `<key>_<uniform>` values). The patched
`libvkbasalt.so` watches the config file's mtime and, on change, re-reads **only** each effect's
`_enabled` gate (swap `applyEffect` ↔ identity passthrough) and its uniform values — **no shader
recompile, no chain rebuild**. So in-game:

- **Solo** switching flips which single gate is on → instant live A/B.
- **Stack** enables multiple gates → they chain in order, live.
- Parameter sliders push new UBO values, live.

This is why switching/stacking is limited to the effects chosen **before** launch — the chain is fixed
at swapchain creation; only the gates and uniforms are live.

## Patched layer

The bundled `libvkbasalt.so` is replaced with a live-reload build (patch:
`patches/vkbasalt-reshade-livereload.patch`, build workflow: `.github/workflows/build-vkbasalt.yml`,
cross-compiled arm64 via meson/NDK). It adds the per-effect enable gate + UBO live-uniform path and is
X11-free. It ships in the existing `extra_libs.tzst` app asset (not `imagefs`), and a **version-aware
extraction** (`EXTRA_LIBS_VERSION`) re-lays it into existing containers on next launch — no imagefs
reinstall, saves/prefixes untouched. Credits and license stay DadSchoorse / Pipetto.

## Persistence

Per-game (Shortcut) and per-container extras:
- `reshadeLoadout` — JSON array in chain order: `[{"name":"Sepia","enabled":true}, …]`
- `reshadeMode` — `solo` (default) or `stack`
- `reshadeParams` — nested per-effect overrides: `{"Sepia":{"Intensity":0.4}, …}`
- `reshadeMasterEnabled` — `0` when the in-game master switch was turned off

Legacy single-effect profiles (`reshadeEffect` + flat `reshadeParams`) **migrate forward** on read.
Shortcut persistence respects `use_container_defaults` (writes to the shortcut when it holds its own
overrides, else the container); mid-session it never flips that flag.

## Notes for review

- Based on the post-refactor `main` (the `XServerDrawerMenu.kt` / `XServerDisplayActivity.java` split).
  The in-game pane is its **own** `XServerDrawerReshadePane.kt`, wired via `DrawerPane.RESHADE` +
  a `RailPaneSpec` + the `when(pane)` dispatch — nothing stuffed into the menu file.
- **Self-contained effects:** each drop-in folder carries its `.fx` plus any `.fxh` includes and
  textures; `reshadeTexturePath`/`reshadeIncludePath` colon-join every staged effect dir. vkBasalt
  predefines the ReShade macros but not the headers, so co-location is required (the catalog packages
  effects this way).
- Depth effects (SSAO/DOF) are out (no depth capture) — a later step.
- Catalog URL is a `buildConfigField` (`RESHADE_CATALOG_URL`) so WinNative can self-host/repoint.
- Fully swallowed on the launch path — a ReShade failure never breaks a game launch.
- Device-proven on WinNative (Adreno/Turnip, DXVK): effect apply, catalog download, in-game live
  enable/disable, **live Solo switching and Stack layering**, and per-effect parameter sliders.

## Follow-ups (not in this PR)

- In-game **pulse preview** so changes show instantly on a fully-paused frame (currently they land on
  the next rendered frame — instant during active gameplay).
- Depth-based effects (depth capture).
