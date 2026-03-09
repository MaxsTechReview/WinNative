# Walkthrough — WinNative UI Overhaul

## Latest Changes

### Menu Restructured
[main_menu.xml](file:///home/max/Build/Emulator/WinNative/app/src/main/res/menu/main_menu.xml) — Reordered:
1. **Stores** → new `StoresFragment` (sign-in/out)
2. **Containers** → `ContainersFragment` (now visible with + button)
3. **Presets** → `SettingsFragment`
4. **Components** → `ContentsFragment`
5. **Drivers** → `AdrenotoolsFragment`
6. **Input Controls** → `InputControlsFragment`

### New StoresFragment
[StoresFragment.java](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/StoresFragment.java):
- Steam: fully functional sign-in/sign-out via `SteamLoginActivity` / `SteamService.logOut()`
- Epic, GOG, Amazon: placeholder rows with "Coming Soon" toasts

### Carousel Polish
[UnifiedActivity.kt](file:///home/max/Build/Emulator/WinNative/app/src/main/java/com/winlator/cmod/UnifiedActivity.kt):
- Removed "INSTALLED GAMES" header
- Capsule width: 200→180dp, image height: 260→234dp (~10% smaller)
- Game title above each capsule, centered, animated alpha (1.0 center, 0.5 sides)
- Title scales with capsule via shared `graphicsLayer`

### Previous Changes
- Tab scroll limit (340dp, ~4 tabs visible)
- Game launch with A: drive mounting
- Artwork: `getHeroUrl` → `getHeaderImageUrl` → `getCapsuleUrl` → CDN fallback
- Fragment NPE fixes (5 files)
- Filter panel, Steam status picker, app icon

## Build
```
BUILD SUCCESSFUL in 3s — 45 actionable tasks
```
