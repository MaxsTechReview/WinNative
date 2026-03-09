# PS5 / Steam Big Picture UI Redesign & Tip Disabling Plan

## Goal Description
1. Disable the "Ask for Tip on Startup" feature by default.
2. Completely redesign the UI to match a sleek, modern, PlayStation 5 / Steam Big Picture Mode style. Focus on landscape orientation, controller-friendliness, deep dark blue Steam color palettes, and fluid animations.

## User Review Required
> [!WARNING]
> This is a significant visual overhaul. It modifies the core colors (backgrounds, surfaces, cards, accents) and the shape/layout of the Tab Bar and Grid Cards. Please review the planned changes below and let me know if you have specific preferences (e.g., exact border radius sizes, specific Steam color hex codes, or if you prefer a side-nav over a top-bar). 

## Proposed Changes

### Configuration
#### [MODIFY] [PrefManager.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/PrefManager.kt)
- Change the default value of the `TIPPED` boolean setting from `false` to `true` to disable the startup tip by default.

---

### Theme & Colors
#### [MODIFY] [Color.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/ui/theme/Color.kt)
- Update `PluviaBackground` to `#1b2838` (Steam Dark Blue).
- Update `PluviaSurface` and `PluviaSurfaceElevated` to `#2a475e` / `#24364c`.
- Update `PluviaCard` to `#2a475e`.
- Update `PluviaPrimary` and `PluviaCyan` to `#66c0f4` (Steam Light Blue).
- Ensure `PluviaForeground` and muted text colors remain high contrast against the new dark blue backgrounds.

#### [MODIFY] [Theme.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/ui/theme/Theme.kt)
- Ensure the `DarkColorScheme` properly absorbs the updated `Color.kt` bindings.
- Tweak surface alpha levels to ensure translucent menus (like the System Menu) blend beautifully into the background.

---

### Component Styling
#### [MODIFY] [LibraryTabBar.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/ui/screen/library/components/LibraryTabBar.kt)
- Reshape the active tab indicator to either a sleek underline or an edge-to-edge glow rather than a tight pill shape.
- Remove harsh secondary backgrounds on tabs to favor a cleaner, "floating" appearance typical of modern console UIs.
- Enhance focus scaling animations for controller bumpers.

#### [MODIFY] [LibraryGridCard.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/ui/screen/library/components/LibraryGridCard.kt)
- Change the `isFocused` modifier stroke to a slightly thicker, brighter line (e.g., solid `#FAFAFA` or `#66c0f4`) to mimic the unmistakable focus state of PS5 tiles.
- Increase the scale slightly when hovered/focused to give a noticeable "pop".
- Adjust shadows/glows around focused cards to amplify the 3D elevation effect.

### Dedicated Frontend Layout
#### [NEW] `LibraryFrontendPane.kt`
- Create a completely unique layout for `PaneType.FRONTEND` modeled after Steam Big Picture Mode.
- Incorporate a massive, dynamic hero-image backdrop of the currently focused game.
- Use a `LazyRow` at the bottom for navigation, allowing users to scroll horizontally through games.
- Include a prominent "Play" button and game title/logo layered over the backdrop.

#### [MODIFY] [LibraryScreen.kt](file:///home/max/Build/GNP/Game/app/src/main/java/app/gamenative/ui/screen/library/LibraryScreen.kt)
- Update the UI to render `LibraryFrontendPane` instead of `LibraryListPane` when `currentPaneType == PaneType.FRONTEND`.
- Route the `onViewChanged` logic seamlessly to this new component.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure no syntax or resolution errors were introduced during the Compose refactor.

### Manual Verification
- Deploy the resulting APK to the device/emulator.
- **Verify Tip:** Launch the app cleanly and ensure the "Ask for tip" does not appear. 
- **Verify Visuals:** Look at the main library page: ensure deep blue background, correctly styled tabs, and clean, legible text.
- **Verify Controller Dynamics:** Use a controller (D-Pad/Bumpers) to navigate tabs and grid items. Focus should cleanly snap to items, tiles should scale elegantly, and no lag should occur during rapid navigation.
