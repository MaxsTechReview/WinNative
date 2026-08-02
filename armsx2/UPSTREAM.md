# ARMSX2 — vendored source

> **Packaging note (current):** the emucore and its runtime data are no longer committed
> here. They are built in the [WinNative-Emu](https://github.com/WinNative-Emu) fork and
> shipped in the [Retro-Consoles](https://github.com/WinNative-Emu/Retro-Consoles) bundle
> the app downloads at runtime. The build/patch details below describe how that fork
> produces the binary and remain accurate for the fork.

This module embeds the Android front-end of **ARMSX2**, a GPL-3.0 PlayStation 2
emulator (a fork of PCSX2) that brings PS2 emulation to ARM64.

- Upstream: https://github.com/ARMSX2/ARMSX2
- Upstream of upstream (PCSX2): https://github.com/pcsx2/pcsx2
- License: GPL-3.0 (`COPYING.GPLv3` in the upstream tree)
- Vendored from commit: `a0bdcbe917bea686333c9683c28b2fca8ed1ed8d` (post 2.6.3)
- Vendored path: `platforms/android/app/src/main`

## Native emulator core

The core (`libemucore_4k.so` / `libemucore_16k.so`) is built **from source** by
default during `assembleDebug`. `build-emucore.sh` fetches ARMSX2 at a pinned
commit (`a0bdcbe917bea686333c9683c28b2fca8ed1ed8d`), applies the tracked patches
under `patches/`, syncs shaderc's third-party deps, and compiles both the 4K and
16K page-size variants with CMake + the NDK, stripped to release size. Fetched
source (`emucore-src/`) and build outputs stay out of git; only the script and
patches are tracked. The build reproduces the full JNI surface of
`kr/co/iefriends/pcsx2/NativeApp.java`.

Pass `-Parmsx2.buildNative=false` to fall back to the prebuilt core under
`prebuilt/emucore/arm64-v8a` (binaries built from the same pin as above), which
is the opt-out safety net.

The supporting libraries under `src/main/jniLibs/arm64-v8a` (ANGLE, `c++_shared`,
`androidx.graphics.path`) remain prebuilt — ANGLE in particular is a separate
Google project, not part of this tree.

### Notable upstream (2.6.3+)

- New ARM64 EE/VU recompiler backend (accuracy/perf; fixes a class of unplayable titles)
- Optional GS multi-threading
- Hardcore mode blocks cheats only; disk patches (including DNAS bypass) remain loadable

### Patches (`patches/`)

- `0001-osd-messages-in-display-rect.patch` — anchor OSD messages to the game
  display rectangle so letterboxing does not push them into black bars
- `0002-android-net-adapter-no-gateway.patch` — Android net adapter gateway handling
- `0003-ps2-dns-force-redirect.patch` — force PS2 DNS redirect for online revival
- `0004-ps2-dns-source-of-truth.patch` — DNS config source-of-truth for DEV9

All upstream copyright and license headers are preserved. This module and the
binaries it contains remain under GPL-3.0. WinNative as a whole is GPL-3.0, so
this integration is license-compatible. Modifications made to adapt the code to
WinNative's build (toolchain, Application ownership, launch entry) are noted in
the affected files.
