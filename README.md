<p align="center">
  <img src="logo.png" alt="WinNative" width="500">
</p>
<p align="center">
    <a href="https://discord.gg/uhTkvGfakU">
        <img src="https://img.shields.io/discord/1358831699814912141?color=5865F2&label=WinNative&logo=discord&logoColor=white"
            alt="Discord">
    </a>
</p>

## WinNative: A Community Built Windows Emulation App for Android

**WinNative** is an advanced, high-performance Windows (x86_64) emulation environment for Android. It bridges the gap between desktop gaming and mobile by unifying the best technologies from **Winlator Bionic** and **Pluvia**.

Designed for enthusiasts and power users, WinNative delivers the full Winlator experience while making it easy to connect your Steam, Epic, and GOG game libraries.

---

### Installation

1. **Download:** Get the latest APK from the [Releases](https://github.com/WinNative-Emu/WinNative/releases) section.
2. **Variants:**
   - `Ludashi`: Forces both Max GPU and CPU clocks on some devices. (Performance Mode trigger).
   - `Vanilla`: Standard package name for side-loading with other forks.
   -  `Antutu`: Forces Max GPU clocks on most devices. (antutu benchmark spoof)
   -  `Pubg`: Standard pubg package name which allows some Game Booster advanced Features.
3. **Setup:** Launch the app, allow the ImageFS to install, and start adding your games manually or sync your library. 

---

### How to Build

**Requirements:** Android Studio, JDK 17, and [Git LFS](https://git-lfs.com). The NDK
(`27.3.13750724`) and CMake are only needed if you build native cores from source (see below).

1. **Clone with submodules and pull LFS objects** (Required):
   ```bash
   git clone --recursive https://github.com/MaxsTechReview/WinNative.git
   cd WinNative
   git lfs pull                          # fetches imagefs
   git submodule update --init --recursive
   ```
2. **Build via Android Studio:** Open the `WinNative` directory, let Gradle sync, then select **Build > Build APK(s)**.
3. **Build via CLI:** Run `./gradlew assembleStandardDebug` (or `.\gradlew.bat` on Windows).

The APK carries no retro console cores. Each core is built from its own fork under the
[WinNative-Emu](https://github.com/WinNative-Emu) org, and
[Retro-Consoles](https://github.com/WinNative-Emu/Retro-Consoles) packs every core plus the
Dolphin and ARMSX2 runtime data into one `retro-consoles.tzst`. The app downloads and
verifies it on demand from **Settings > Retro > Download console cores**, so a core update
no longer needs an app release. To change a core, change its fork and re-run the
Retro-Consoles bundle workflow.

---

### Retro Console Support

WinNative can also run classic console games alongside your PC library. Retro games live in the same Library and launch just like PC games, but run on an embedded libretro backend instead of Wine.

Supported systems (bundled cores):

| System | Core | ROM extensions |
| --- | --- | --- |
| NES | FCEUmm | `.nes` `.unf` `.unif` |
| SNES | Snes9x | `.smc` `.sfc` `.swc` `.fig` |
| Game Boy / Color | Gambatte | `.gb` `.gbc` |
| Game Boy Advance | mGBA | `.gba` |
| Genesis / Mega Drive, Master System, Game Gear | Genesis Plus GX | `.gen` `.md` `.smd` `.sms` `.gg` |
| Nintendo 64 | Mupen64Plus-Next | `.n64` `.z64` `.v64` |
| PlayStation | Beetle PSX | `.cue` `.chd` `.pbp` `.m3u` `.iso` |
| PlayStation 2 | ARMSX2 (PCSX2 fork) | `.iso` `.chd` `.cso` `.bin` |

Cores ship **prebuilt** (committed via Git LFS) and are used by default; they are built from
source with the opt-in flags above (see `cores/` for the libretro cores and
`armsx2/build-emucore.sh` / `dolphin/build-emucore.sh` for the PS2 and GameCube/Wii cores).
PlayStation 2 online play is supported through the emulated DEV9 network adapter (see the
in-game **Online** tab).

**How to use:** In the Library, tap **Add Custom Game** and select a ROM instead of an `.exe`. WinNative detects the console and adds the game to your Library. Tap **Play** to launch it with on-screen touch controls and physical gamepad support; the in-game menu (Back button or on-screen **MENU**) offers save/load state, reset, and fast-forward. PlayStation and PlayStation 2 BIOS files can be imported from **Settings → Retro**.

### Frame Generation

WinNative can interpolate extra frames between the ones your game actually renders, using the
Lossless Scaling frame generation shaders. Interpolation runs **on the Android side**, inside
WinNative's own Vulkan compositor rather than inside the Wine container, so it works with any
graphics API Wine can drive — DXVK, WineD3D or native Vulkan alike.

**You must own [Lossless Scaling](https://store.steampowered.com/) on Steam.** Its shaders are
not redistributable, so nothing ships with the APK. WinNative reads them out of your own copy of
`Lossless.dll`, translates them from DXBC to SPIR-V once, and caches the result in app storage.
The DLL is parsed as data and never executed.

**Setup:** sign in to Steam, install Lossless Scaling, then open **Container Settings → Frame
Generation**. WinNative finds the DLL automatically from your Steam library; if it can't, use
**Select Lossless.dll…** to point at it. The in-game **FG** tab stays disabled until the shaders
import successfully.

**In-game controls** live in the **FG** tab of the session drawer, between HUD and Gyro:

| Control | What it does |
| --- | --- |
| Generate Frames | Master toggle |
| Adaptive Target | Aim for a specific output rate (60/90/120/144/165) instead of a fixed multiplier |
| Multiplier | 2× / 3× / 4× — generated frames per rendered frame |
| Flow Scale | 25–100%, resolution of the optical-flow pyramid; lower is cheaper and softer |
| FPS Limiter | Caps the game's own frame rate, from 15 fps upward |

**What to expect.** Frame generation costs **one extra frame of input latency** — interpolating
between two frames means holding the newer one back. It also needs spare display refresh:
generated frames occupy vblanks, so WinNative sizes the multiplier against your panel's refresh
rate and the game's actual frame rate, and will hand back generated frames rather than take real
ones from the game. A game already running near your panel's refresh rate has nothing to gain.
Pairing a multiplier with an FPS limiter that divides the refresh rate evenly (120 Hz with a
60 fps cap at 2×, or 40 at 3×) gives the most even pacing.

---

### Contributing

We welcome community contributions! Feel free to open a pull request for bug fixes, driver updates, UI improvements, or anything else you'd like to add.

Please match the existing code style and ensure any AI-assisted code is thoroughly reviewed and tested before submission.

---

### Credits & Acknowledgments

- **Original Winlator** by [brunodev85](https://github.com/brunodev85/winlator)
- **Winlator Bionic** by [Pipetto-crypto](https://github.com/Pipetto-crypto/winlator)
- **Pluvia** features by the [Pluvia](https://github.com/oxters168/Pluvia) / [GameNative](https://github.com/utkarshdalal/GameNative) community
- **Mesa/Turnip** contributions by the [Mesa3D](https://www.mesa3d.org/) team
- **Goldberg Steam Emulator** by [Mr. Goldberg](https://gitlab.com/Mr_Goldberg/goldberg_emulator), maintained by [Detanup01](https://github.com/Detanup01/gbe_fork)
- **LibretroDroid** by [Filippo Scognamiglio](https://github.com/Swordfish90/LibretroDroid) (GPL-3.0) — the embedded libretro host for retro console support
- **libretro / RetroArch** and the individual core authors, built from source: [FCEUmm](https://github.com/libretro/libretro-fceumm), [Snes9x](https://github.com/libretro/snes9x), [Gambatte](https://github.com/libretro/gambatte-libretro), [mGBA](https://github.com/libretro/mgba), [Genesis Plus GX](https://github.com/libretro/Genesis-Plus-GX), [Mupen64Plus-Next](https://github.com/libretro/mupen64plus-libretro-nx), [Beetle PSX](https://github.com/libretro/beetle-psx-libretro)
- **ARMSX2** by the [ARMSX2](https://github.com/ARMSX2/ARMSX2) team (GPL-3.0) — the PlayStation 2 core, a fork of **[PCSX2](https://github.com/pcsx2/pcsx2)** (GPL-3.0), built from source into `libemucore`. PS2 online play uses PCSX2's DEV9 network adapter
- **lsfg-vk** by [PancakeTAS](https://github.com/PancakeTAS/lsfg-vk) (GPL-3.0-or-later) — the original Vulkan reimplementation of the Lossless Scaling frame generation chain
- **Eden Emulator Project** by the [eden](https://git.eden-emu.dev/eden-emu/eden) team (GPL-3.0-or-later) — the Vulkan port of that chain that WinNative's frame generation is derived from. See [Frame generation — what came from Eden](#frame-generation--what-came-from-eden) below
- **DXVK** by [Philip Rebohle and contributors](https://github.com/doitsujin/dxvk) (zlib/libpng) — the `dxbc` shader translator, vendored at `app/src/main/cpp/thirdparty/dxbc` to convert the frame generation shaders to SPIR-V
- **Lossless Scaling** (Steam) — the source of the frame generation shaders. They are read from the user's own installed copy at runtime; none are redistributed with WinNative

#### Frame generation — what came from Eden

WinNative's frame generation exists because the [Eden Emulator Project](https://git.eden-emu.dev/eden-emu/eden)
had already solved the hard part: getting the Lossless Scaling compute chain running correctly
on Vulkan, on mobile GPUs. The port here started from Eden's work and still carries it. Their
copyright notices are preserved in every file that derives from them, under GPL-3.0-or-later.

Derived from Eden (jointly with **[lsfg-vk](https://github.com/PancakeTAS/lsfg-vk)**, which Eden
themselves ported from):

| Source file | What it provides |
| --- | --- |
| `lsfg_chain.*` | The shape of the whole chain — which of the 25 shaders run, in what order, and what each stage feeds the next |
| `lsfg_mipmaps.*` | The flow pyramid the rest of the chain is built on |
| `lsfg_alpha.*` | Per-level feature extraction, including the batched-barrier dispatch pattern the rest of the chain follows |
| `lsfg_beta.*` | The coarse flow estimate the refinement stages start from |
| `lsfg_gamma.*` | Coarse-to-fine flow refinement, one instance per pyramid level |
| `lsfg_delta.*` | The extra refinement and detail passes on the finest levels |
| `lsfg_generate.*` | The final warp that produces the interpolated frame |
| `lsfg_common.*` | The Vulkan plumbing all of the above sit on — image, sampler and buffer wrappers, the barrier builder, the descriptor writer, and the pass/pipeline helper |

Derived from Eden specifically:

| Source file | What it provides |
| --- | --- |
| `lsfg_pacer.*` | Deciding how many frames to generate per real frame |
| `lsfg_shaders.*` | Turning the extracted shader blobs into Vulkan shader modules |

Getting the descriptor layouts, barrier placement and dispatch geometry of a 25-shader chain
right is not something you arrive at by reading the shaders; it is the part that takes the
debugging. Eden did that work, and this port would not have been possible without it.

What WinNative added on top is the Windows and Android side of it: reading the shader blobs out
of a user's own Lossless Scaling install (`lsfg_dll.*`), translating them when only DXBC is
available (`lsfg_dxbc.*`), the JNI surface (`lsfg_jni.*`), driver probing (`lsfg_probe.*`), and
wiring the chain into WinNative's compositor and swapchain (`vkr_lsfg.*`).
