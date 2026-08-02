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
