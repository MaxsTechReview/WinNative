# WinNative v0.4.0-beta — DRAFT

### Main Changes:

**Retro console emulation**
 - WinNative now plays retro console games alongside your Wine/PC titles. Retro games live in the same Library and launch the same way, but run on a libretro backend — or, for PS2 and GameCube/Wii, on a full emulator in its own process.
 - Supported systems: NES (FCEUmm), SNES (Snes9x), Game Boy / Color (Gambatte), Game Boy Advance (mGBA, with gpSP for link-cable multiplayer), Genesis / Master System / Game Gear (Genesis Plus GX), Nintendo 64 (Mupen64Plus-Next), PlayStation (Beetle PSX), PlayStation 2 (ARMSX2), and GameCube / Wii (Dolphin, standalone Vulkan).
 - **No emulator core ships inside the APK.** Cores are downloaded on demand from **Settings › Retro** as a single verified bundle, extracted atomically so an interrupted install can never leave a half-installed core set behind. Updates are an exact checksum comparison — the screen tells you the installed build date and only downloads when you tap.
 - **RetroAchievements** support (softcore) with login, achievement lists and in-game unlock toasts.
 - **347 core options** generated from each core's own source, grouped into Display / Sound / Performance / Controls / System, as per-console defaults and per-game overrides.
 - Editable touch layouts with per-game profiles, netplay for the libretro cores and Dolphin, save states, cloud save sync, and a draggable performance HUD — fully translated into all 22 locales.
 - The guide button now opens the retro menu in the 3D engine too, and the Stadium ROM row imports through the system file picker instead of only printing a path.

**ReShade — drop-in effects, in-app catalog and live in-game control**
 - ReShade `.fx` effects now work on Vulkan-backed (DXVK/VKD3D) games, with a per-game and per-container **loadout**: add effects, reorder them, and pick **Solo** (one at a time, live A/B switch) or **Stack** (layer any subset in chain order).
 - A built-in **catalog** lets you search, download and delete effects without leaving the app, and each effect's own parameters are exposed for tuning.
 - A ReShade pane in the in-game drawer gives you a master toggle, the Solo/Stack selector and per-effect control — no relaunch needed.
 - Built on vkBasalt, brought into the Winlator/Cmod lineage by Pipetto-crypto.

**Graphics drivers & wrappers**
 - **Vulkan 1.4** has been added to the graphics driver version list and is now the default; the exported version is clamped to what the driver actually reports, so picking 1.4 on a 1.3 driver no longer advertises an unsupported version.
 - Updated the graphics wrapper to Pipetto-crypto's latest, with a patched BCn view-format list.
 - Updated the Wrapper-Gamenative driver binary — external memory sharing now uses dmabuf heaps with a `/dev/ion` fallback.
 - New **Zink Mode** toggle (Unix/Windows) for arm64ec containers, saved per container and overridable per shortcut.
 - Fixed OpenGL on arm64 and corrected the HUD reporting Vulkan when a game had actually requested Zink.

**Frame pacing & performance**
 - Rewritten FPS limiter: lower latency, and selecting a refresh rate no longer secretly turns the cap on — choosing 60Hz used to lock you to a fighting ~55 FPS.
 - Fixed the FPS HUD showing the panel refresh rate instead of true game FPS; uncapped games are no longer paced down to the panel, and a game's own vsync choice now survives.
 - Added a **container-level display refresh rate** setting, matching the one in shortcut settings; a rate set on a shortcut still wins.
 - Background services are pinned to small cores, aggressive service startup has been fixed, and a 32-bit fix has been folded in.
 - More FEX presets, and `pan_nave` in the Advanced tab has been fixed.

**MangoHud-style overlay**
 - A new Mango-style HUD overlay joins the existing HUD, with grouped settings, controller navigation, brighter accents and a battery temperature row.
 - The main HUD has been taken off the present path with matched 500ms sampling windows, and the Mango panel now uses a narrower layout so it takes less of the screen.

**Input & touch controls**
 - **Combo button swipes**: sliding off a touch button no longer leaves your finger dead — buttons press on entry and release on exit, the d-pad hands off steering, and radial menus swipe open and close.
 - The control editor's snapping grid is gone — drags are free pixel motion and no longer land short of your finger.
 - Gyro mouse activation can now be bound to mouse and keyboard buttons, not just gamepad buttons, and works from a physical mouse, on-screen controls or the touchpad.
 - All control binding slots are dispatched, with a new reverse-order toggle.
 - New default touch draw (slate) plus 8 selectable draws and 10 themes; custom colors still override themes.
 - Fixed tap-to-click leaving buttons latched down when the setting was disabled, Lumina D-pad chevrons pointing inward, and the Lumina range button drawing as a giant circle instead of a pill.

**Library, Steam & file manager**
 - New **artwork scraper for custom games**, pulling art from SteamGridDB and Steam, with SteamGridDB now searching by game name by default.
 - Steam records a durable install path, so games stay recognized instead of showing up as missing or re-downloading.
 - The file manager gained **multi-select** for batch copy, cut and delete, with a single byte-weighted progress bar for the whole paste.
 - `.msi`, `.bat` and `.cmd` can now be picked *and* launched from the exe pickers and the file manager — they run through msiexec or cmd instead of failing at CreateProcess.
 - Fixed tapping the Add Custom Game name field not opening the keyboard.

**Containers**
 - Fixed duplicated containers failing to boot, and duplicates now keep the full source config (prefix architecture and emulator versions included) with their shortcuts pointing at the copy rather than the original.
 - Fixed the surface effect setting being lost when set during container creation and ignored at launch.
 - Added the missing virtual channel to the Wine debug channel list.

**Audio**
 - Adopted Bruno's new XAudio stack.

### Minor Changes:
 - New 32-bit and 64-bit input tests, available from both the hero boot popup and the Start Menu, translated into all 22 locales.
 - Application logging fixes: cold app starts no longer erase the log, so a crash can be pulled on the first occurrence, and the redundant `application.old.log` is gone.
 - Fixed graphics driver selection on Polish and Hindi locales, where stale translated arrays silently saved the wrong driver and the wrapper was never extracted at launch.
 - Removed the hairline of surface left on screen by the closed drawer sheet.
 - Fixed custom mapping drive containers.
 - App source files have been reorganized for readability, and the Antutu flavor has been added and fixed.
 - Build plumbing: LibretroDroid moved out of the main repo and is fetched during configuration, so a fresh clone resolves in the IDE without a manual build.

### Contributors
Huge thanks to everyone who contributed, this release wouldn't exist without you:
 - @Xnick417x — 39 commits (ReShade groundwork, frame pacing and FPS limiter, Mango HUD, touch/control input, file manager, drivers and wrappers, container fixes, many more)
 - @MaxsTechReview — 4 commits (retro console emulation, core bundle delivery, 3D engine guide button + Stadium ROM import, build plumbing)
 - @SadMoment — 2 commits (custom game artwork scraper, SteamGridDB name search)
 - @The412Banner — 1 commit (ReShade: drop-in effects, catalog, loadouts and live in-game control)

## New Contributors
* @SadMoment made their first contribution in https://github.com/WinNative-Emu/WinNative/pull/634
* @The412Banner made their first contribution in https://github.com/WinNative-Emu/WinNative/pull/631

**Full Changelog**: https://github.com/WinNative-Emu/WinNative/compare/v0.3.1-beta...v0.4.0-beta
